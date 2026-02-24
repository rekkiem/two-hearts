package com.twohearts.data.api

import android.util.Log
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import io.ktor.utils.io.core.*
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "ApiService"

sealed class ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>()
    data class Error(val message: String, val code: Int = 0) : ApiResult<Nothing>()
}

@Singleton
class ApiService @Inject constructor(
    private val client: HttpClient,
    private val baseUrl: String
) {

    private val api = "$baseUrl/api/v1"

    // ---- Auth ----

    suspend fun requestMagicLink(email: String): ApiResult<MagicLinkResponse> = safeCall {
        client.post("$api/auth/magic-link") {
            contentType(ContentType.Application.Json)
            setBody(MagicLinkRequest(email))
        }.body()
    }

    suspend fun verifyToken(token: String, deviceId: String = "android"): ApiResult<TokenResponse> = safeCall {
        client.post("$api/auth/verify") {
            contentType(ContentType.Application.Json)
            setBody(VerifyTokenRequest(token, deviceId))
        }.body()
    }

    suspend fun refreshTokens(refreshToken: String, deviceId: String = "android"): ApiResult<TokenResponse> = safeCall {
        client.post("$api/auth/refresh") {
            contentType(ContentType.Application.Json)
            setBody(RefreshRequest(refreshToken, deviceId))
        }.body()
    }

    // ---- Profile ----

    suspend fun getMyProfile(): ApiResult<ProfileResponse> = safeCall {
        client.get("$api/profiles/me").body()
    }

    suspend fun createProfile(req: CreateProfileRequest): ApiResult<ProfileResponse> = safeCall {
        client.post("$api/profiles") {
            contentType(ContentType.Application.Json)
            setBody(req)
        }.body()
    }

    suspend fun updateProfile(req: CreateProfileRequest): ApiResult<ProfileResponse> = safeCall {
        client.put("$api/profiles") {
            contentType(ContentType.Application.Json)
            setBody(req)
        }.body()
    }

    suspend fun uploadPhoto(imageBytes: ByteArray, mimeType: String): ApiResult<Map<String, String>> = safeCall {
        client.post("$api/profiles/photo") {
            setBody(MultiPartFormDataContent(formData {
                append("photo", imageBytes, Headers.build {
                    append(HttpHeaders.ContentType, mimeType)
                    append(HttpHeaders.ContentDisposition, "filename=photo.jpg")
                })
            }))
        }.body()
    }

    // ---- Intent ----

    suspend fun getTodayQuestion(): ApiResult<IntentQuestionResponse> = safeCall {
        client.get("$api/intents/question").body()
    }

    suspend fun getTodayIntent(): ApiResult<IntentResponse?> = safeCall {
        val response = client.get("$api/intents/today")
        if (response.status == HttpStatusCode.NoContent) null
        else response.body()
    }

    suspend fun submitIntent(questionId: String, answer: String): ApiResult<IntentResponse> = safeCall {
        client.post("$api/intents") {
            contentType(ContentType.Application.Json)
            setBody(SubmitIntentRequest(questionId, answer))
        }.body()
    }

    // ---- Matches ----

    suspend fun getMatches(): ApiResult<List<MatchResponse>> = safeCall {
        client.get("$api/matches").body()
    }

    suspend fun interactWithMatch(matchId: String, action: String): ApiResult<MatchInteractionResponse> = safeCall {
        client.post("$api/matches/$matchId/interact") {
            contentType(ContentType.Application.Json)
            setBody(MatchInteractionRequest(action))
        }.body()
    }

    // ---- Chat ----

    suspend fun getConversations(): ApiResult<List<ConversationResponse>> = safeCall {
        client.get("$api/conversations").body()
    }

    suspend fun getMessages(conversationId: String, limit: Int = 50): ApiResult<List<MessageResponse>> = safeCall {
        client.get("$api/conversations/$conversationId/messages") {
            parameter("limit", limit)
        }.body()
    }

    suspend fun sendMessage(conversationId: String, content: String): ApiResult<MessageResponse> = safeCall {
        client.post("$api/conversations/$conversationId/messages") {
            contentType(ContentType.Application.Json)
            setBody(SendMessageRequest(content))
        }.body()
    }

    // ---- Safe wrapper ----

    private suspend inline fun <reified T> safeCall(crossinline block: suspend () -> T): ApiResult<T> {
        return try {
            ApiResult.Success(block())
        } catch (e: io.ktor.client.plugins.ClientRequestException) {
            val msg = try { e.response.body<ErrorResponse>().error } catch (_: Exception) { e.message ?: "Request failed" }
            Log.w(TAG, "Client error ${e.response.status}: $msg")
            ApiResult.Error(msg, e.response.status.value)
        } catch (e: io.ktor.client.plugins.ServerResponseException) {
            Log.e(TAG, "Server error: ${e.message}")
            ApiResult.Error("Server error. Please try again.", e.response.status.value)
        } catch (e: Exception) {
            Log.e(TAG, "Network error: ${e.message}", e)
            ApiResult.Error(e.message ?: "Network error")
        }
    }
}
