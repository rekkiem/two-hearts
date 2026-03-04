package com.twohearts.services

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.websocket.*
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap
import java.util.UUID

private val logger = KotlinLogging.logger {}

@Serializable
data class SignalMessage(
    val type: String,        // "offer" | "answer" | "ice-candidate" | "hang-up" | "call-ready" | "call-rejected" | "busy"
    val from: String,
    val to: String? = null,
    val sdp: String? = null,
    val candidate: String? = null,
    val sdpMid: String? = null,
    val sdpMLineIndex: Int? = null,
    val conversationId: String? = null
)

@Serializable
data class CallSession(
    val id: String,
    val conversationId: String,
    val callerId: String,
    val calleeId: String,
    val status: String,      // "ringing" | "active" | "ended"
    val startedAt: Long = System.currentTimeMillis()
)

data class SignalPeer(
    val userId: String,
    val conversationId: String,
    val session: DefaultWebSocketSession
)

/**
 * WebRTC Signaling Service
 * Relays SDP offers/answers and ICE candidates between peers.
 * Tracks active call sessions per conversation.
 */
class VideoSignalingService {
    private val json = Json { ignoreUnknownKeys = true }

    // conversationId -> list of connected peers (max 2)
    private val peers = ConcurrentHashMap<String, MutableList<SignalPeer>>()

    // callId -> CallSession
    private val activeCalls = ConcurrentHashMap<String, CallSession>()

    fun registerPeer(peer: SignalPeer) {
        peers.getOrPut(peer.conversationId) { mutableListOf() }.add(peer)
        logger.info { "Signal peer registered: user=${peer.userId} conv=${peer.conversationId}" }
    }

    fun unregisterPeer(peer: SignalPeer) {
        peers[peer.conversationId]?.remove(peer)
        logger.info { "Signal peer unregistered: user=${peer.userId}" }
    }

    fun initiateCall(conversationId: String, callerId: String, calleeId: String): CallSession {
        val existing = activeCalls.values.find { it.conversationId == conversationId && it.status != "ended" }
        if (existing != null) {
            throw IllegalStateException("A call is already active in this conversation")
        }
        val session = CallSession(
            id             = UUID.randomUUID().toString(),
            conversationId = conversationId,
            callerId       = callerId,
            calleeId       = calleeId,
            status         = "ringing"
        )
        activeCalls[session.id] = session
        return session
    }

    fun endCall(callId: String) {
        activeCalls[callId]?.let { activeCalls[callId] = it.copy(status = "ended") }
    }

    fun getActiveCall(conversationId: String): CallSession? =
        activeCalls.values.find { it.conversationId == conversationId && it.status != "ended" }

    suspend fun relay(conversationId: String, fromUserId: String, message: SignalMessage) {
        val recipients = peers[conversationId]?.filter { it.userId != fromUserId }
        if (recipients.isNullOrEmpty()) {
            logger.warn { "No peer to relay to in conv=$conversationId from=$fromUserId (type=${message.type})" }
            return
        }
        val encoded = json.encodeToString(message)
        recipients.forEach { peer ->
            try {
                peer.session.send(Frame.Text(encoded))
                logger.debug { "Relayed ${message.type} to ${peer.userId}" }
            } catch (e: ClosedSendChannelException) {
                logger.debug { "Peer ${peer.userId} channel closed during relay" }
            }
        }

        // Track call state
        when (message.type) {
            "answer" -> {
                val call = getActiveCall(conversationId)
                if (call != null) activeCalls[call.id] = call.copy(status = "active")
            }
            "hang-up" -> {
                val call = getActiveCall(conversationId)
                if (call != null) activeCalls[call.id] = call.copy(status = "ended")
            }
        }
    }

    fun getPeersInConversation(conversationId: String): List<String> =
        peers[conversationId]?.map { it.userId } ?: emptyList()
}
