package com.twohearts.services

import com.twohearts.NotFoundException
import com.twohearts.database.*
import com.twohearts.models.*
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.Period
import java.util.UUID
import kotlin.math.*

private val logger = KotlinLogging.logger {}

class MatchingService(private val embeddingService: EmbeddingService) {

    // ====================================================
    // DAILY INTENT
    // ====================================================

    fun getTodayQuestion(): IntentQuestionResponse = transaction {
        // Pick a question based on day-of-year rotation
        val dayOfYear = LocalDate.now().dayOfYear
        val questions = IntentQuestionsTable
            .select(IntentQuestionsTable.id, IntentQuestionsTable.text, IntentQuestionsTable.category)
            .where { IntentQuestionsTable.isActive eq true }
            .toList()

        if (questions.isEmpty()) throw IllegalStateException("No active questions")
        val q = questions[dayOfYear % questions.size]

        IntentQuestionResponse(
            id       = q[IntentQuestionsTable.id].value.toString(),
            text     = q[IntentQuestionsTable.text],
            category = q[IntentQuestionsTable.category]
        )
    }

    fun submitIntent(userId: String, questionId: String, answer: String): IntentResponse {
        require(answer.length >= 10) { "Answer must be at least 10 characters" }
        require(answer.length <= 500) { "Answer must be at most 500 characters" }

        val today = LocalDate.now().toString()
        val embedding = embeddingService.embed(answer)
        val vecStr = embeddingService.floatArrayToVector(embedding)

        return transaction {
            // Upsert (one intent per user per day)
            val existing = DailyIntentsTable
                .select(DailyIntentsTable.id)
                .where {
                    (DailyIntentsTable.userId eq UUID.fromString(userId)) and
                    (DailyIntentsTable.intentDate eq today)
                }
                .singleOrNull()

            val intentId: String

            if (existing != null) {
                intentId = existing[DailyIntentsTable.id].value.toString()
                DailyIntentsTable.update({
                    (DailyIntentsTable.userId eq UUID.fromString(userId)) and
                    (DailyIntentsTable.intentDate eq today)
                }) {
                    it[this.questionId] = UUID.fromString(questionId)
                    it[answerText]      = answer
                }
            } else {
                intentId = DailyIntentsTable.insertAndGetId {
                    it[this.userId]     = UUID.fromString(userId)
                    it[this.questionId] = UUID.fromString(questionId)
                    it[answerText]      = answer
                    it[intentDate]      = today
                    it[createdAt]       = OffsetDateTime.now()
                }.value.toString()
            }

            // Store embedding via raw SQL
            exec("UPDATE daily_intents SET answer_embedding = '$vecStr'::vector WHERE id = '$intentId'")

            // Also refresh profile embedding with latest intent
            val profile = ProfilesTable
                .select(ProfilesTable.bio, ProfilesTable.occupation)
                .where { ProfilesTable.userId eq UUID.fromString(userId) }
                .singleOrNull()

            if (profile != null) {
                val refreshedVec = embeddingService.embedProfile(
                    bio = profile[ProfilesTable.bio],
                    occupation = profile[ProfilesTable.occupation],
                    recentIntentAnswer = answer
                )
                exec("UPDATE profiles SET embedding = '${embeddingService.floatArrayToVector(refreshedVec)}'::vector WHERE user_id = '$userId'")
            }

            IntentResponse(intentId, questionId, answer, today)
        }
    }

    fun getTodayIntent(userId: String): IntentResponse? = transaction {
        val today = LocalDate.now().toString()
        DailyIntentsTable
            .select(DailyIntentsTable.id, DailyIntentsTable.questionId,
                    DailyIntentsTable.answerText, DailyIntentsTable.intentDate)
            .where {
                (DailyIntentsTable.userId eq UUID.fromString(userId)) and
                (DailyIntentsTable.intentDate eq today)
            }
            .singleOrNull()
            ?.let {
                IntentResponse(
                    id         = it[DailyIntentsTable.id].value.toString(),
                    questionId = it[DailyIntentsTable.questionId].value.toString(),
                    answerText = it[DailyIntentsTable.answerText],
                    intentDate = it[DailyIntentsTable.intentDate]
                )
            }
    }

    // ====================================================
    // MATCHING
    // ====================================================

    fun getTodayMatches(userId: String): List<MatchResponse> {
        val today = LocalDate.now().toString()
        val userProfile = transaction {
            ProfilesTable.select(ProfilesTable.columns)
                .where { ProfilesTable.userId eq UUID.fromString(userId) }
                .singleOrNull()
        } ?: return emptyList()

        // Already-created matches for today
        val existingMatches = getExistingMatches(userId, today)
        if (existingMatches.isNotEmpty()) {
            return buildMatchResponses(userId, existingMatches)
        }

        // Generate new matches for today
        val newMatches = generateMatches(userId, userProfile, today)
        return buildMatchResponses(userId, newMatches)
    }

    fun interactWithMatch(userId: String, matchId: String, action: String): MatchInteractionResponse {
        require(action in listOf("like", "pass")) { "Action must be 'like' or 'pass'" }

        return transaction {
            val match = MatchesTable
                .select(MatchesTable.columns)
                .where { MatchesTable.id eq UUID.fromString(matchId) }
                .singleOrNull()
                ?: throw NotFoundException("Match not found")

            val userAId = match[MatchesTable.userAId].value.toString()
            val userBId = match[MatchesTable.userBId].value.toString()
            val currentStatus = match[MatchesTable.status]

            require(userId == userAId || userId == userBId) { "Not a participant of this match" }

            if (action == "pass") {
                MatchesTable.update({ MatchesTable.id eq UUID.fromString(matchId) }) {
                    it[status]    = "passed"
                    it[updatedAt] = OffsetDateTime.now()
                }
                return@transaction MatchInteractionResponse("passed", null, "Match passed")
            }

            // action == "like"
            val isUserA = userId == userAId
            val newStatus = when {
                currentStatus == "pending" && isUserA  -> "user_a_liked"
                currentStatus == "pending" && !isUserA -> "user_b_liked"
                currentStatus == "user_b_liked" && isUserA -> "mutual"
                currentStatus == "user_a_liked" && !isUserA -> "mutual"
                else -> currentStatus
            }

            MatchesTable.update({ MatchesTable.id eq UUID.fromString(matchId) }) {
                it[status]    = newStatus
                it[updatedAt] = OffsetDateTime.now()
            }

            var conversationId: String? = null
            if (newStatus == "mutual") {
                // Create conversation
                conversationId = ConversationsTable.insertAndGetId {
                    it[this.matchId] = UUID.fromString(matchId)
                    it[this.userAId] = UUID.fromString(userAId)
                    it[this.userBId] = UUID.fromString(userBId)
                    it[createdAt]    = OffsetDateTime.now()
                }.value.toString()
                logger.info { "Mutual match! Conversation $conversationId created for match $matchId" }
            }

            MatchInteractionResponse(
                status         = newStatus,
                conversationId = conversationId,
                message        = if (newStatus == "mutual") "It's a match! 💕" else "Interest noted!"
            )
        }
    }

    // ====================================================
    // PRIVATE: Match generation
    // ====================================================

    private fun generateMatches(
        userId: String,
        userRow: ResultRow,
        today: String
    ): List<ResultRow> {
        val uid = UUID.fromString(userId)
        val prefGenders  = ProfileService.dbToGenders(userRow[ProfilesTable.prefGenders])
        val prefMinAge   = userRow[ProfilesTable.prefMinAge]
        val prefMaxAge   = userRow[ProfilesTable.prefMaxAge]
        val prefMaxDist  = userRow[ProfilesTable.prefMaxDistKm]
        val userLat      = userRow[ProfilesTable.lat]
        val userLng      = userRow[ProfilesTable.lng]

        // Previously seen users (this week)
        val weekAgo = LocalDate.now().minusDays(7).toString()
        val seenIds = transaction {
            val seenAsA = MatchesTable
                .select(MatchesTable.userBId)
                .where { (MatchesTable.userAId eq uid) and (MatchesTable.matchDate greaterEq weekAgo) }
                .map { it[MatchesTable.userBId].value.toString() }.toSet()
            val seenAsB = MatchesTable
                .select(MatchesTable.userAId)
                .where { (MatchesTable.userBId eq uid) and (MatchesTable.matchDate greaterEq weekAgo) }
                .map { it[MatchesTable.userAId].value.toString() }.toSet()
            seenAsA + seenAsB + setOf(userId)
        }

        // Semantic candidates from pgvector
        val candidates = findSemanticCandidates(userId, seenIds, limit = 50)

        if (candidates.isEmpty()) return emptyList()

        // Score and filter
        val scoredCandidates = candidates.mapNotNull { (candidateId, semanticSim) ->
            val profile = transaction {
                ProfilesTable.select(ProfilesTable.columns)
                    .where { ProfilesTable.userId eq UUID.fromString(candidateId) }
                    .singleOrNull()
            } ?: return@mapNotNull null

            val age = ProfileService.calculateAge(profile[ProfilesTable.birthDate])
            val candidateGender = profile[ProfilesTable.genderIdentity]

            // Hard filters
            if (age < prefMinAge || age > prefMaxAge) return@mapNotNull null
            if (prefGenders.isNotEmpty() && candidateGender !in prefGenders) return@mapNotNull null

            // Mutual preference check
            val candPrefGenders = ProfileService.dbToGenders(profile[ProfilesTable.prefGenders])
            val userGender = userRow[ProfilesTable.genderIdentity]
            if (candPrefGenders.isNotEmpty() && userGender !in candPrefGenders) return@mapNotNull null

            val candMinAge = profile[ProfilesTable.prefMinAge]
            val candMaxAge = profile[ProfilesTable.prefMaxAge]
            val userAge    = ProfileService.calculateAge(userRow[ProfilesTable.birthDate])
            if (userAge < candMinAge || userAge > candMaxAge) return@mapNotNull null

            // Distance filter
            val distanceKm = if (userLat != null && userLng != null &&
                    profile[ProfilesTable.lat] != null && profile[ProfilesTable.lng] != null) {
                haversine(userLat, userLng, profile[ProfilesTable.lat]!!, profile[ProfilesTable.lng]!!)
            } else null

            if (distanceKm != null && distanceKm > prefMaxDist) return@mapNotNull null

            // Intent score (if both submitted today)
            val intentScore = getIntentScore(userId, candidateId, today)

            // Composite score
            val geoScore = distanceKm?.let { computeGeoScore(it, prefMaxDist.toDouble()) } ?: 0.5
            val composite = 0.5 * semanticSim + 0.3 * intentScore + 0.2 * geoScore

            Triple(profile, composite, generateExplainer(semanticSim, intentScore, distanceKm))
        }

        if (scoredCandidates.isEmpty()) return emptyList()

        // Top 3 matches
        val top3 = scoredCandidates.sortedByDescending { it.second }.take(3)

        return transaction {
            top3.map { (profile, score, explainer) ->
                val candidateId = profile[ProfilesTable.userId].toString()
                val (aId, bId) = canonicalOrder(userId, candidateId)
                val matchId = MatchesTable.insertAndGetId {
                    it[userAId]       = UUID.fromString(aId)
                    it[userBId]       = UUID.fromString(bId)
                    it[this.score]    = score
                    it[this.explainer] = explainerToDb(explainer)
                    it[status]        = "pending"
                    it[matchDate]     = today
                    it[createdAt]     = OffsetDateTime.now()
                    it[updatedAt]     = OffsetDateTime.now()
                }.value.toString()
                logger.debug { "Created match $matchId (score=${"%.2f".format(score)})" }
                MatchesTable.select(MatchesTable.columns)
                    .where { MatchesTable.id eq UUID.fromString(matchId) }
                    .single()
            }
        }
    }

    private fun findSemanticCandidates(
        userId: String,
        excludeIds: Set<String>,
        limit: Int
    ): List<Pair<String, Double>> {
        return transaction {
            // Get user's current embedding
            val row = exec(
                "SELECT embedding::text FROM profiles WHERE user_id = '$userId' AND embedding IS NOT NULL"
            ) { rs -> if (rs.next()) rs.getString("embedding") else null }
                ?: return@transaction emptyList()

            val excludeClause = if (excludeIds.isEmpty()) ""
            else "AND user_id NOT IN (${excludeIds.joinToString(",") { "'$it'" }})"

            val results = mutableListOf<Pair<String, Double>>()
            exec("""
                SELECT user_id::text, 
                       1 - (embedding <=> '$row'::vector) AS similarity
                FROM profiles
                WHERE user_id != '$userId'
                  AND embedding IS NOT NULL
                  AND profile_complete = TRUE
                  $excludeClause
                ORDER BY embedding <=> '$row'::vector
                LIMIT $limit
            """) { rs ->
                while (rs.next()) {
                    results.add(rs.getString("user_id") to rs.getDouble("similarity"))
                }
            }
            results
        }
    }

    private fun getIntentScore(userId: String, candidateId: String, today: String): Double {
        return transaction {
            val userIntent = exec(
                "SELECT answer_embedding::text FROM daily_intents WHERE user_id = '$userId' AND intent_date = '$today' AND answer_embedding IS NOT NULL"
            ) { rs -> if (rs.next()) rs.getString("answer_embedding") else null }
                ?: return@transaction 0.5

            val candIntent = exec(
                "SELECT answer_embedding::text FROM daily_intents WHERE user_id = '$candidateId' AND intent_date = '$today' AND answer_embedding IS NOT NULL"
            ) { rs -> if (rs.next()) rs.getString("answer_embedding") else null }
                ?: return@transaction 0.5

            val userVec = parseVector(userIntent)
            val candVec = parseVector(candIntent)
            embeddingService.cosineSimilarity(userVec, candVec).toDouble()
        }
    }

    private fun getExistingMatches(userId: String, today: String): List<ResultRow> = transaction {
        val uid = UUID.fromString(userId)
        MatchesTable.select(MatchesTable.columns)
            .where {
                ((MatchesTable.userAId eq uid) or (MatchesTable.userBId eq uid)) and
                (MatchesTable.matchDate eq today) and
                (MatchesTable.status neq "passed")
            }
            .toList()
    }

    private fun buildMatchResponses(userId: String, matchRows: List<ResultRow>): List<MatchResponse> = transaction {
        matchRows.mapNotNull { match ->
            val matchId   = match[MatchesTable.id].value.toString()
            val userAId   = match[MatchesTable.userAId].value.toString()
            val userBId   = match[MatchesTable.userBId].value.toString()
            val otherId   = if (userId == userAId) userBId else userAId
            val status    = match[MatchesTable.status]
            val explainer = dbToExplainer(match[MatchesTable.explainer])
            val score     = match[MatchesTable.score]

            val otherProfile = ProfilesTable.select(ProfilesTable.columns)
                .where { ProfilesTable.userId eq UUID.fromString(otherId) }
                .singleOrNull() ?: return@mapNotNull null

            val convId = ConversationsTable
                .select(ConversationsTable.id)
                .where { ConversationsTable.matchId eq UUID.fromString(matchId) }
                .singleOrNull()
                ?.let { it[ConversationsTable.id].value.toString() }

            MatchResponse(
                matchId        = matchId,
                userId         = otherId,
                displayName    = otherProfile[ProfilesTable.displayName],
                age            = ProfileService.calculateAge(otherProfile[ProfilesTable.birthDate]),
                genderIdentity = otherProfile[ProfilesTable.genderIdentity],
                bio            = otherProfile[ProfilesTable.bio],
                photoUrl       = otherProfile[ProfilesTable.photoUrl],
                city           = otherProfile[ProfilesTable.city],
                score          = score,
                explainer      = explainer,
                status         = status,
                conversationId = convId
            )
        }
    }

    // ---- Utilities ----

    private fun computeGeoScore(distKm: Double, maxDist: Double): Double {
        if (distKm <= 0) return 1.0
        if (distKm >= maxDist) return 0.0
        return exp(-2.5 * (distKm / maxDist))
    }

    private fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return R * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    private fun canonicalOrder(a: String, b: String): Pair<String, String> =
        if (a < b) a to b else b to a

    private fun generateExplainer(semantic: Double, intent: Double, distanceKm: Double?): List<String> {
        val points = mutableListOf<String>()
        points += when {
            semantic >= 0.8 -> "Strong alignment in values and life perspective"
            semantic >= 0.6 -> "Meaningful common ground in how you see the world"
            else -> "Complementary views that could spark interesting conversations"
        }
        if (intent > 0.5) {
            points += when {
                intent >= 0.75 -> "Today's reflections resonated deeply — you're on the same wavelength"
                else -> "Your daily reflections share a common thread"
            }
        }
        distanceKm?.let { d ->
            points += when {
                d < 5 -> "Very close to you — just ${d.toInt()} km away"
                d < 30 -> "${d.toInt()} km away"
                else -> "${d.toInt()} km away"
            }
        }
        return points.take(3)
    }

    private fun explainerToDb(points: List<String>): String =
        "{${points.joinToString(",") { "\"${it.replace("\"", "\\\"")}\"" }}}"

    private fun dbToExplainer(raw: String): List<String> {
        if (raw == "{}") return emptyList()
        return raw.removeSurrounding("{", "}")
            .split("\",\"")
            .map { it.removeSurrounding("\"").replace("\\\"", "\"") }
    }

    private fun parseVector(vecStr: String): FloatArray =
        vecStr.trim('[', ']').split(",")
            .map { it.trim().toFloat() }
            .toFloatArray()
}
