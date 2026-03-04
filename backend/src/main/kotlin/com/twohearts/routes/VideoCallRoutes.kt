package com.twohearts.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.twohearts.services.SignalMessage
import com.twohearts.services.SignalPeer
import com.twohearts.services.VideoSignalingService
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val logger = KotlinLogging.logger {}
private val signalJson = Json { ignoreUnknownKeys = true }

fun Route.videoCallRoutes(videoService: VideoSignalingService) {
    route("/video") {
        // POST /api/v1/video/{conversationId}/call — initiate a call
        post("/{conversationId}/call") {
            val callerId       = call.principal<JWTPrincipal>()!!.payload.subject
            val conversationId = call.parameters["conversationId"]!!
            val calleeId       = call.request.headers["X-Callee-Id"]
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "X-Callee-Id header required"))

            try {
                val session = videoService.initiateCall(conversationId, callerId, calleeId)
                call.respond(HttpStatusCode.Created, mapOf(
                    "callId"         to session.id,
                    "conversationId" to conversationId,
                    "status"         to session.status
                ))
            } catch (e: IllegalStateException) {
                call.respond(HttpStatusCode.Conflict, mapOf("error" to e.message))
            }
        }

        // DELETE /api/v1/video/{callId} — end a call
        delete("/{callId}") {
            val callId = call.parameters["callId"]!!
            videoService.endCall(callId)
            call.respond(HttpStatusCode.OK, mapOf("status" to "ended"))
        }

        // GET /api/v1/video/{conversationId}/status — check active call
        get("/{conversationId}/status") {
            val conversationId = call.parameters["conversationId"]!!
            val activeCall     = videoService.getActiveCall(conversationId)
            val peersOnline    = videoService.getPeersInConversation(conversationId)
            call.respond(HttpStatusCode.OK, mapOf(
                "activeCall"    to activeCall,
                "peersOnline"   to peersOnline,
                "canStartCall"  to (peersOnline.size == 2)
            ))
        }
    }
}

/**
 * WebSocket signaling for WebRTC peer-to-peer video
 * URL: /ws/signal/{conversationId}?token=<jwt>
 *
 * Message flow:
 *   Caller  → offer        → Server → Callee
 *   Callee  → answer       → Server → Caller
 *   Both    → ice-candidate → Server → other peer
 *   Either  → hang-up      → Server → other peer
 */
fun Route.webSocketSignalingRoutes(videoService: VideoSignalingService, jwtSecret: String, jwtIssuer: String) {
    val algorithm = Algorithm.HMAC256(jwtSecret)

    webSocket("/ws/signal/{conversationId}") {
        val convId = call.parameters["conversationId"]
            ?: run { close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Missing conversationId")); return@webSocket }

        val token = call.request.queryParameters["token"]
            ?: run { close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Missing token")); return@webSocket }

        val userId = try {
            JWT.require(algorithm).withIssuer(jwtIssuer).build().verify(token).subject
        } catch (e: Exception) {
            close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Invalid token"))
            return@webSocket
        }

        val peer = SignalPeer(userId, convId, this)
        videoService.registerPeer(peer)

        // Notify other peer that we're ready
        videoService.relay(convId, userId, SignalMessage(
            type           = "peer-connected",
            from           = userId,
            conversationId = convId
        ))
        logger.info { "Signal WS connected: user=$userId conv=$convId" }

        try {
            for (frame in incoming) {
                if (frame is Frame.Text) {
                    val raw = frame.readText()
                    try {
                        val msg = signalJson.decodeFromString<SignalMessage>(raw)
                        // Validate sender matches JWT subject
                        if (msg.from != userId) {
                            send(Frame.Text(signalJson.encodeToString(SignalMessage(
                                type = "error", from = "server",
                                sdp = "from field must match authenticated user"
                            ))))
                            continue
                        }
                        when (msg.type) {
                            "offer", "answer", "ice-candidate", "hang-up",
                            "call-rejected", "call-ready", "busy" -> {
                                videoService.relay(convId, userId, msg)
                            }
                            else -> {
                                logger.warn { "Unknown signal type: ${msg.type} from $userId" }
                            }
                        }
                    } catch (e: Exception) {
                        logger.warn { "Invalid signal message from $userId: ${e.message}" }
                    }
                }
            }
        } catch (e: ClosedReceiveChannelException) {
            logger.debug { "Signal stream closed for $userId" }
        } finally {
            videoService.unregisterPeer(peer)
            videoService.relay(convId, userId, SignalMessage(
                type = "peer-disconnected", from = userId, conversationId = convId
            ))
        }
    }
}
