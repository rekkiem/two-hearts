package com.twohearts.services

import com.twohearts.NotFoundException
import com.twohearts.database.ProfilesTable
import com.twohearts.models.CreateProfileRequest
import com.twohearts.models.ProfileResponse
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.Period
import java.util.UUID

private val logger = KotlinLogging.logger {}

// FIX: Explicit column list to avoid querying the unmapped 'embedding' vector column
private val PROFILE_COLS = listOf(
    ProfilesTable.userId, ProfilesTable.displayName, ProfilesTable.birthDate,
    ProfilesTable.genderIdentity, ProfilesTable.bio, ProfilesTable.occupation,
    ProfilesTable.relationshipIntent, ProfilesTable.lat, ProfilesTable.lng,
    ProfilesTable.city, ProfilesTable.photoUrl, ProfilesTable.prefMinAge,
    ProfilesTable.prefMaxAge, ProfilesTable.prefGenders, ProfilesTable.prefMaxDistKm,
    ProfilesTable.profileComplete, ProfilesTable.createdAt, ProfilesTable.updatedAt
)

data class ProfileData(
    val userId: String, val displayName: String, val birthDate: String,
    val genderIdentity: String, val bio: String?, val occupation: String?,
    val relationshipIntent: String, val lat: Double?, val lng: Double?,
    val city: String?, val photoUrl: String?, val prefMinAge: Int,
    val prefMaxAge: Int, val prefGenders: List<String>, val prefMaxDistKm: Int,
    val profileComplete: Boolean
)

class ProfileService(
    private val embeddingService: EmbeddingService,
    private val minioService: MinioService
) {

    fun upsertProfile(userId: String, req: CreateProfileRequest): ProfileResponse = transaction {
        val uid = UUID.fromString(userId)
        val isComplete = req.displayName.isNotBlank() && req.birthDate.isNotBlank() && req.genderIdentity.isNotBlank()
        val exists = ProfilesTable.select(ProfilesTable.userId).where { ProfilesTable.userId eq uid }.any()

        if (!exists) {
            ProfilesTable.insert {
                it[this.userId]        = uid
                it[displayName]        = req.displayName
                it[birthDate]          = req.birthDate
                it[genderIdentity]     = req.genderIdentity
                it[bio]                = req.bio
                it[occupation]         = req.occupation
                it[relationshipIntent] = req.relationshipIntent
                it[lat]                = req.lat
                it[lng]                = req.lng
                it[city]               = req.city
                it[prefMinAge]         = req.prefMinAge
                it[prefMaxAge]         = req.prefMaxAge
                it[prefGenders]        = gendersToDb(req.prefGenders)
                it[prefMaxDistKm]      = req.prefMaxDistKm
                it[profileComplete]    = isComplete
                it[createdAt]          = OffsetDateTime.now()
                it[updatedAt]          = OffsetDateTime.now()
            }
        } else {
            ProfilesTable.update({ ProfilesTable.userId eq uid }) {
                it[displayName]        = req.displayName
                it[birthDate]          = req.birthDate
                it[genderIdentity]     = req.genderIdentity
                it[bio]                = req.bio
                it[occupation]         = req.occupation
                it[relationshipIntent] = req.relationshipIntent
                it[lat]                = req.lat
                it[lng]                = req.lng
                it[city]               = req.city
                it[prefMinAge]         = req.prefMinAge
                it[prefMaxAge]         = req.prefMaxAge
                it[prefGenders]        = gendersToDb(req.prefGenders)
                it[prefMaxDistKm]      = req.prefMaxDistKm
                it[profileComplete]    = isComplete
                it[updatedAt]          = OffsetDateTime.now()
            }
        }

        updateEmbeddingInternal(userId, req.bio, req.occupation, null)
        getProfileOrThrow(userId)
    }

    fun getProfile(userId: String): ProfileResponse? = transaction {
        ProfilesTable.select(PROFILE_COLS)
            .where { ProfilesTable.userId eq UUID.fromString(userId) }
            .singleOrNull()?.toResponse()
    }

    fun getProfileOrThrow(userId: String): ProfileResponse =
        getProfile(userId) ?: throw NotFoundException("Profile not found")

    fun updatePhoto(userId: String, bytes: ByteArray, contentType: String): String {
        val url = minioService.uploadPhoto(userId, bytes, contentType)
        transaction {
            ProfilesTable.update({ ProfilesTable.userId eq UUID.fromString(userId) }) {
                it[photoUrl]  = url
                it[updatedAt] = OffsetDateTime.now()
            }
        }
        return url
    }

    fun updateEmbedding(userId: String, bio: String?, occupation: String?, intentAnswer: String?) =
        transaction { updateEmbeddingInternal(userId, bio, occupation, intentAnswer) }

    private fun updateEmbeddingInternal(userId: String, bio: String?, occupation: String?, intentAnswer: String?) {
        val vec    = embeddingService.embedProfile(bio, occupation, intentAnswer)
        val vecStr = embeddingService.floatArrayToVector(vec)
        exec("UPDATE profiles SET embedding = '$vecStr'::vector WHERE user_id = '$userId'")
        logger.debug { "Embedding updated for $userId" }
    }

    fun getPublicProfile(userId: String): ProfileData? = transaction {
        ProfilesTable.select(PROFILE_COLS)
            .where { ProfilesTable.userId eq UUID.fromString(userId) }
            .singleOrNull()?.toData()
    }

    private fun ResultRow.toResponse() = ProfileResponse(
        userId           = this[ProfilesTable.userId].value.toString(),
        displayName      = this[ProfilesTable.displayName],
        birthDate        = this[ProfilesTable.birthDate],
        age              = calculateAge(this[ProfilesTable.birthDate]),
        genderIdentity   = this[ProfilesTable.genderIdentity],
        bio              = this[ProfilesTable.bio],
        occupation       = this[ProfilesTable.occupation],
        relationshipIntent = this[ProfilesTable.relationshipIntent],
        city             = this[ProfilesTable.city],
        photoUrl         = this[ProfilesTable.photoUrl],
        prefMinAge       = this[ProfilesTable.prefMinAge],
        prefMaxAge       = this[ProfilesTable.prefMaxAge],
        prefGenders      = dbToGenders(this[ProfilesTable.prefGenders]),
        prefMaxDistKm    = this[ProfilesTable.prefMaxDistKm],
        profileComplete  = this[ProfilesTable.profileComplete]
    )

    private fun ResultRow.toData() = ProfileData(
        userId           = this[ProfilesTable.userId].value.toString(),
        displayName      = this[ProfilesTable.displayName],
        birthDate        = this[ProfilesTable.birthDate],
        genderIdentity   = this[ProfilesTable.genderIdentity],
        bio              = this[ProfilesTable.bio],
        occupation       = this[ProfilesTable.occupation],
        relationshipIntent = this[ProfilesTable.relationshipIntent],
        lat              = this[ProfilesTable.lat],
        lng              = this[ProfilesTable.lng],
        city             = this[ProfilesTable.city],
        photoUrl         = this[ProfilesTable.photoUrl],
        prefMinAge       = this[ProfilesTable.prefMinAge],
        prefMaxAge       = this[ProfilesTable.prefMaxAge],
        prefGenders      = dbToGenders(this[ProfilesTable.prefGenders]),
        prefMaxDistKm    = this[ProfilesTable.prefMaxDistKm],
        profileComplete  = this[ProfilesTable.profileComplete]
    )

    companion object {
        fun calculateAge(birthDateStr: String): Int = try {
            Period.between(LocalDate.parse(birthDateStr), LocalDate.now()).years
        } catch (_: Exception) { 0 }

        fun gendersToDb(genders: List<String>): String =
            if (genders.isEmpty()) "{}" else "{${genders.joinToString(",")}}"

        fun dbToGenders(raw: String): List<String> {
            val cleaned = raw.removeSurrounding("{", "}")
            return if (cleaned.isBlank()) emptyList() else cleaned.split(",").map { it.trim() }
        }
    }
}
