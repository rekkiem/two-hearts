package com.twohearts.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.twohearts.models.*
import com.twohearts.services.ChatService
import com.twohearts.services.ConnectionRegistry
import com.twohearts.services.WsSession
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val logger = KotlinLogging.logger {}
private val json   = Json { ignoreUnknownKeys = true }

fun Route.chatRoutes(chatService: ChatService) {
    route("/conversations") {

        // GET /api/v1/conversations
        get {
            val userId = call.principal<JWTPrincipal>()!!.payload.subject
            val convs  = chatService.getConversations(userId)
            call.respond(HttpStatusCode.OK, convs)
        }

        // GET /api/v1/conversations/{id}/messages
        get("/{id}/messages") {
            val userId = call.principal<JWTPrincipal>()!!.payload.subject
            val convId = call.parameters["id"]!!
            val limit  = call.request.queryParameters["limit"]?.toIntOrNull() ?: 50
            val msgs   = chatService.getMessages(userId, convId, limit)
            call.respond(HttpStatusCode.OK, msgs)
        }

        // POST /api/v1/conversations/{id}/messages (REST fallback)
        post("/{id}/messages") {
            val userId = call.principal<JWTPrincipal>()!!.payload.subject
            val convId = call.parameters["id"]!!
            val req    = call.receive<SendMessageRequest>()
            val msg    = chatService.saveMessage(convId, userId, req.content)

            // Relay via WebSocket to other participant
            val conv = chatService.getConversation(convId)
            if (conv != null) {
                val outgoing = WsOutgoing(
                    type           = "message",
                    messageId      = msg.id,
                    conversationId = convId,
                    senderId       = userId,
                    content        = msg.content,
                    sentAt         = msg.sentAt
                )
                ConnectionRegistry.broadcast(convId, userId, outgoing)
            }

            call.respond(HttpStatusCode.Created, msg)
        }

        // POST /api/v1/conversations/{id}/read
        post("/{id}/read/{messageId}") {
            val userId    = call.principal<JWTPrincipal>()!!.payload.subject
            val convId    = call.parameters["id"]!!
            val messageId = call.parameters["messageId"]!!
            chatService.markRead(userId, convId, messageId)
            call.respond(HttpStatusCode.OK, mapOf("status" to "ok"))
        }
    }
}

/**
 * WebSocket endpoint: /ws/chat/{conversationId}?token=<jwt>
 * Token passed as query param since Android WebSocket clients can't set headers.
 */
fun Route.webSocketChatRoutes(chatService: ChatService, jwtSecret: String, jwtIssuer: String) {
    val algorithm = Algorithm.HMAC256(jwtSecret)

    webSocket("/ws/chat/{conversationId}") {
        val convId = call.parameters["conversationId"]
            ?: run { close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Missing conversationId")); return@webSocket }

        val token = call.request.queryParameters["token"]
            ?: run { close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Missing token")); return@webSocket }

        // Validate JWT
        val userId = try {
            val decoded = JWT.require(algorithm).withIssuer(jwtIssuer).build().verify(token)
            decoded.subject
        } catch (e: Exception) {
            logger.warn { "WS JWT invalid: ${e.message}" }
            close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Invalid token"))
            return@webSocket
        }

        // Verify user is a participant
        val conv = chatService.getConversation(convId)
        if (conv == null || (userId != conv.userAId && userId != conv.userBId)) {
            close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Not a participant"))
            return@webSocket
        }

        val wsSession = WsSession(userId, convId, this)
        ConnectionRegistry.register(wsSession)

        // Send presence to other participant
        ConnectionRegistry.broadcast(convId, userId, WsOutgoing(
            type     = "presence",
            senderId = userId,
            content  = "online"
        ))

        logger.info { "WS connected: user=$userId conv=$convId" }

        try {
            for (frame in incoming) {
                when (frame) {
                    is Frame.Text -> handleTextFrame(frame.readText(), userId, convId, chatService)
                    is Frame.Ping -> send(Frame.Pong(frame.data))
                    is Frame.Close -> break
                    else -> {}
                }
            }
        } catch (e: ClosedReceiveChannelException) {
            logger.debug { "WS stream closed for user $userId" }
        } catch (e: Exception) {
            logger.error(e) { "WS error for user $userId" }
        } finally {
            ConnectionRegistry.unregister(wsSession)
            ConnectionRegistry.broadcast(convId, userId, WsOutgoing(
                type     = "presence",
                senderId = userId,
                content  = "offline"
            ))
            logger.info { "WS disconnected: user=$userId conv=$convId" }
        }
    }
}

private suspend fun DefaultWebSocketSession.handleTextFrame(
    raw: String,
    userId: String,
    convId: String,
    chatService: ChatService
) {
    val incoming = try {
        json.decodeFromString<WsIncoming>(raw)
    } catch (e: Exception) {
        send(Frame.Text(json.encodeToString(WsOutgoing(type = "error", error = "Invalid JSON"))))
        return
    }

    when (incoming.type) {
        "message" -> {
            val content = incoming.content
            if (content.isNullOrBlank()) {
                send(Frame.Text(json.encodeToString(WsOutgoing(type = "error", error = "Empty message"))))
                return
            }
            val msg = chatService.saveMessage(convId, userId, content)

            // ACK to sender
            send(Frame.Text(json.encodeToString(WsOutgoing(
                type           = "message_ack",
                messageId      = msg.id,
                conversationId = convId,
                sentAt         = msg.sentAt
            ))))

            // Relay to other participant(s) in conversation
            ConnectionRegistry.broadcast(convId, userId, WsOutgoing(
                type           = "message",
                messageId      = msg.id,
                conversationId = convId,
                senderId       = userId,
                content        = msg.content,
                sentAt         = msg.sentAt
            ))
        }

        "read" -> {
            val messageId = incoming.messageId ?: return
            chatService.markRead(userId, convId, messageId)
            ConnectionRegistry.broadcast(convId, userId, WsOutgoing(
                type      = "read",
                messageId = messageId,
                senderId  = userId
            ))
        }

        "typing" -> {
            ConnectionRegistry.broadcast(convId, userId, WsOutgoing(
                type     = "typing",
                senderId = userId,
                content  = incoming.content ?: "start"  // "start" | "stop"
            ))
        }

        "ping" -> {
            send(Frame.Text(json.encodeToString(WsOutgoing(type = "pong"))))
        }

        else -> {
            send(Frame.Text(json.encodeToString(WsOutgoing(type = "error", error = "Unknown type: ${incoming.type}"))))
        }
    }
}
