package com.twohearts.database

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestampWithTimeZone

object UsersTable : UUIDTable("users") {
    val email     = text("email").uniqueIndex()
    val isActive  = bool("is_active").default(true)
    val createdAt = timestampWithTimeZone("created_at")
    val updatedAt = timestampWithTimeZone("updated_at")
}

object MagicLinksTable : UUIDTable("magic_links") {
    val userId    = reference("user_id", UsersTable)
    val tokenHash = text("token_hash").uniqueIndex()
    val expiresAt = timestampWithTimeZone("expires_at")
    val usedAt    = timestampWithTimeZone("used_at").nullable()
    val createdAt = timestampWithTimeZone("created_at")
}

object RefreshTokensTable : UUIDTable("refresh_tokens") {
    val userId    = reference("user_id", UsersTable)
    val tokenHash = text("token_hash").uniqueIndex()
    val deviceId  = text("device_id").nullable()
    val expiresAt = timestampWithTimeZone("expires_at")
    val revokedAt = timestampWithTimeZone("revoked_at").nullable()
    val createdAt = timestampWithTimeZone("created_at")
}

object ProfilesTable : Table("profiles") {
    val userId             = uuid("user_id").references(UsersTable.id)
    val displayName        = text("display_name")
    val birthDate          = text("birth_date")  // stored as ISO date string
    val genderIdentity     = text("gender_identity")
    val bio                = text("bio").nullable()
    val occupation         = text("occupation").nullable()
    val relationshipIntent = text("relationship_intent").default("open_to_anything")
    val lat                = double("lat").nullable()
    val lng                = double("lng").nullable()
    val city               = text("city").nullable()
    val photoUrl           = text("photo_url").nullable()
    val prefMinAge         = integer("pref_min_age").default(18)
    val prefMaxAge         = integer("pref_max_age").default(99)
    val prefGenders        = text("pref_genders").default("{}")   // postgres text[] stored as string
    val prefMaxDistKm      = integer("pref_max_dist_km").default(100)
    // embedding column managed via raw SQL — not in Exposed schema
    val profileComplete    = bool("profile_complete").default(false)
    val createdAt          = timestampWithTimeZone("created_at")
    val updatedAt          = timestampWithTimeZone("updated_at")

    override val primaryKey = PrimaryKey(userId)
}

object IntentQuestionsTable : UUIDTable("intent_questions") {
    val text      = text("text")
    val category  = text("category").default("values")
    val isActive  = bool("is_active").default(true)
    val createdAt = timestampWithTimeZone("created_at")
}

object DailyIntentsTable : UUIDTable("daily_intents") {
    val userId     = reference("user_id", UsersTable)
    val questionId = reference("question_id", IntentQuestionsTable)
    val answerText = text("answer_text")
    val intentDate = text("intent_date")   // ISO date string
    val createdAt  = timestampWithTimeZone("created_at")
}

object MatchesTable : UUIDTable("matches") {
    val userAId   = reference("user_a_id", UsersTable)
    val userBId   = reference("user_b_id", UsersTable)
    val score     = double("score").default(0.0)
    val explainer = text("explainer").default("{}")  // postgres text[] as string
    val status    = text("status").default("pending")
    val matchDate = text("match_date")
    val createdAt = timestampWithTimeZone("created_at")
    val updatedAt = timestampWithTimeZone("updated_at")
}

object ConversationsTable : UUIDTable("conversations") {
    val matchId       = reference("match_id", MatchesTable).uniqueIndex()
    val userAId       = reference("user_a_id", UsersTable)
    val userBId       = reference("user_b_id", UsersTable)
    val lastMessageAt = timestampWithTimeZone("last_message_at").nullable()
    val createdAt     = timestampWithTimeZone("created_at")
}

object MessagesTable : UUIDTable("messages") {
    val conversationId = reference("conversation_id", ConversationsTable)
    val senderId       = reference("sender_id", UsersTable)
    val content        = text("content")
    val readAt         = timestampWithTimeZone("read_at").nullable()
    val sentAt         = timestampWithTimeZone("sent_at")
}
