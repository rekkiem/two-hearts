package com.twohearts.services

import com.twohearts.NotFoundException
import com.twohearts.database.*
import com.twohearts.models.*
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.websocket.*
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.OffsetDateTime
import java.util.*
import java.util.concurrent.ConcurrentHashMap

private val logger = KotlinLogging.logger {}

data class WsSession(
    val userId: String,
    val conversationId: String,
    val session: DefaultWebSocketSession
)

/**
 * In-memory WebSocket connection registry.
 * For multi-instance deployment, replace with Redis Pub/Sub.
 */
object ConnectionRegistry {
    // conversationId -> list of active sessions
    private val connections = ConcurrentHashMap<String, MutableList<WsSession>>()

    fun register(ws: WsSession) {
        connections.getOrPut(ws.conversationId) { mutableListOf() }.add(ws)
        logger.debug { "WS registered: user=${ws.userId} conv=${ws.conversationId}" }
    }

    fun unregister(ws: WsSession) {
        connections[ws.conversationId]?.remove(ws)
        logger.debug { "WS unregistered: user=${ws.userId} conv=${ws.conversationId}" }
    }

    suspend fun broadcast(conversationId: String, senderUserId: String, outgoing: WsOutgoing) {
        val json = Json.encodeToString(outgoing)
        connections[conversationId]?.filter { it.userId != senderUserId }?.forEach { ws ->
            try {
                ws.session.send(Frame.Text(json))
            } catch (e: ClosedSendChannelException) {
                logger.debug { "Session closed while broadcasting to ${ws.userId}" }
            }
        }
    }

    suspend fun sendToUser(conversationId: String, userId: String, outgoing: WsOutgoing) {
        val json = Json.encodeToString(outgoing)
        connections[conversationId]?.filter { it.userId == userId }?.forEach { ws ->
            try {
                ws.session.send(Frame.Text(json))
            } catch (e: ClosedSendChannelException) {
                logger.debug { "Session closed for user $userId" }
            }
        }
    }
}

class ChatService {

    fun getConversations(userId: String): List<ConversationResponse> = transaction {
        val uid = UUID.fromString(userId)

        ConversationsTable.select(ConversationsTable.columns)
            .where { (ConversationsTable.userAId eq uid) or (ConversationsTable.userBId eq uid) }
            .orderBy(ConversationsTable.lastMessageAt, SortOrder.DESC_NULLS_LAST)
            .mapNotNull { conv ->
                val convId  = conv[ConversationsTable.id].value.toString()
                val otherId = if (conv[ConversationsTable.userAId].value.toString() == userId)
                    conv[ConversationsTable.userBId].value.toString()
                else
                    conv[ConversationsTable.userAId].value.toString()

                val otherProfile = ProfilesTable.select(
                        ProfilesTable.userId, ProfilesTable.displayName, ProfilesTable.photoUrl
                    )
                    .where { ProfilesTable.userId eq UUID.fromString(otherId) }
                    .singleOrNull() ?: return@mapNotNull null

                val lastMessage = MessagesTable.select(MessagesTable.columns)
                    .where { MessagesTable.conversationId eq UUID.fromString(convId) }
                    .orderBy(MessagesTable.sentAt, SortOrder.DESC)
                    .limit(1)
                    .singleOrNull()
                    ?.toResponse()

                ConversationResponse(
                    id        = convId,
                    matchId   = conv[ConversationsTable.matchId].value.toString(),
                    otherUser = ConversationUserInfo(
                        userId      = otherId,
                        displayName = otherProfile[ProfilesTable.displayName],
                        photoUrl    = otherProfile[ProfilesTable.photoUrl]
                    ),
                    lastMessage    = lastMessage,
                    lastMessageAt  = conv[ConversationsTable.lastMessageAt]?.toString()
                )
            }
    }

    fun getConversation(conversationId: String): ConversationRow? = transaction {
        ConversationsTable.select(ConversationsTable.columns)
            .where { ConversationsTable.id eq UUID.fromString(conversationId) }
            .singleOrNull()
            ?.let { row ->
                ConversationRow(
                    id      = conversationId,
                    userAId = row[ConversationsTable.userAId].value.toString(),
                    userBId = row[ConversationsTable.userBId].value.toString()
                )
            }
    }

    fun getMessages(userId: String, conversationId: String, limit: Int = 50, before: String? = null): List<MessageResponse> {
        verifyParticipant(userId, conversationId)
        return transaction {
            val query = MessagesTable.select(MessagesTable.columns)
                .where { MessagesTable.conversationId eq UUID.fromString(conversationId) }
                .orderBy(MessagesTable.sentAt, SortOrder.DESC)
                .limit(limit)
            query.map { it.toResponse() }.reversed()
        }
    }

    fun saveMessage(conversationId: String, senderId: String, content: String): MessageResponse {
        require(content.isNotBlank()) { "Message content cannot be empty" }
        require(content.length <= 5000) { "Message too long" }

        return transaction {
            val msgId = MessagesTable.insertAndGetId {
                it[this.conversationId] = UUID.fromString(conversationId)
                it[this.senderId]       = UUID.fromString(senderId)
                it[this.content]        = content
                it[sentAt]              = OffsetDateTime.now()
            }.value.toString()

            ConversationsTable.update({ ConversationsTable.id eq UUID.fromString(conversationId) }) {
                it[lastMessageAt] = OffsetDateTime.now()
            }

            MessagesTable.select(MessagesTable.columns)
                .where { MessagesTable.id eq UUID.fromString(msgId) }
                .single()
                .toResponse()
        }
    }

    fun markRead(userId: String, conversationId: String, messageId: String) {
        verifyParticipant(userId, conversationId)
        transaction {
            MessagesTable.update({
                (MessagesTable.id eq UUID.fromString(messageId)) and
                (MessagesTable.senderId neq UUID.fromString(userId))
            }) {
                it[readAt] = OffsetDateTime.now()
            }
        }
    }

    private fun verifyParticipant(userId: String, conversationId: String) {
        val conv = getConversation(conversationId)
            ?: throw NotFoundException("Conversation not found")
        require(userId == conv.userAId || userId == conv.userBId) {
            "You are not a participant in this conversation"
        }
    }

    private fun ResultRow.toResponse(): MessageResponse = MessageResponse(
        id             = this[MessagesTable.id].value.toString(),
        conversationId = this[MessagesTable.conversationId].value.toString(),
        senderId       = this[MessagesTable.senderId].value.toString(),
        content        = this[MessagesTable.content],
        sentAt         = this[MessagesTable.sentAt].toString(),
        readAt         = this[MessagesTable.readAt]?.toString()
    )
}

data class ConversationRow(val id: String, val userAId: String, val userBId: String)
