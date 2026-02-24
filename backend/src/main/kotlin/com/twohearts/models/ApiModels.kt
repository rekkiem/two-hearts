package com.twohearts.models

import kotlinx.serialization.Serializable

// ---- Auth ----

@Serializable
data class MagicLinkRequest(val email: String)

@Serializable
data class MagicLinkResponse(val message: String)

@Serializable
data class VerifyTokenRequest(
    val token: String,
    val deviceId: String = "default"
)

@Serializable
data class TokenResponse(
    val accessToken: String,
    val refreshToken: String,
    val userId: String,
    val expiresIn: Int = 900
)

@Serializable
data class RefreshRequest(val refreshToken: String, val deviceId: String = "default")

// ---- Profile ----

@Serializable
data class CreateProfileRequest(
    val displayName: String,
    val birthDate: String,        // ISO: "1995-06-15"
    val genderIdentity: String,
    val bio: String? = null,
    val occupation: String? = null,
    val relationshipIntent: String = "open_to_anything",
    val lat: Double? = null,
    val lng: Double? = null,
    val city: String? = null,
    val prefMinAge: Int = 18,
    val prefMaxAge: Int = 99,
    val prefGenders: List<String> = emptyList(),
    val prefMaxDistKm: Int = 100
)

@Serializable
data class ProfileResponse(
    val userId: String,
    val displayName: String,
    val birthDate: String,
    val age: Int,
    val genderIdentity: String,
    val bio: String?,
    val occupation: String?,
    val relationshipIntent: String,
    val city: String?,
    val photoUrl: String?,
    val prefMinAge: Int,
    val prefMaxAge: Int,
    val prefGenders: List<String>,
    val prefMaxDistKm: Int,
    val profileComplete: Boolean
)

@Serializable
data class PhotoUploadResponse(val photoUrl: String)

// ---- Intent ----

@Serializable
data class IntentQuestionResponse(
    val id: String,
    val text: String,
    val category: String
)

@Serializable
data class SubmitIntentRequest(
    val questionId: String,
    val answer: String
)

@Serializable
data class IntentResponse(
    val id: String,
    val questionId: String,
    val answerText: String,
    val intentDate: String
)

// ---- Matching ----

@Serializable
data class MatchResponse(
    val matchId: String,
    val userId: String,
    val displayName: String,
    val age: Int,
    val genderIdentity: String,
    val bio: String?,
    val photoUrl: String?,
    val city: String?,
    val score: Double,
    val explainer: List<String>,
    val status: String,
    val conversationId: String?
)

@Serializable
data class MatchInteractionRequest(val action: String) // "like" | "pass"

@Serializable
data class MatchInteractionResponse(
    val status: String,
    val conversationId: String?,
    val message: String
)

// ---- Chat ----

@Serializable
data class ConversationResponse(
    val id: String,
    val matchId: String,
    val otherUser: ConversationUserInfo,
    val lastMessage: MessageResponse?,
    val lastMessageAt: String?
)

@Serializable
data class ConversationUserInfo(
    val userId: String,
    val displayName: String,
    val photoUrl: String?
)

@Serializable
data class MessageResponse(
    val id: String,
    val conversationId: String,
    val senderId: String,
    val content: String,
    val sentAt: String,
    val readAt: String?
)

@Serializable
data class SendMessageRequest(val content: String)

// ---- WebSocket ----

@Serializable
data class WsIncoming(
    val type: String,          // "message" | "read" | "ping"
    val content: String? = null,
    val messageId: String? = null
)

@Serializable
data class WsOutgoing(
    val type: String,          // "message" | "read" | "presence" | "error" | "pong"
    val messageId: String? = null,
    val conversationId: String? = null,
    val senderId: String? = null,
    val content: String? = null,
    val sentAt: String? = null,
    val error: String? = null
)

// ---- Error ----

@Serializable
data class ErrorResponse(val error: String)
