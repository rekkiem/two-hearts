package com.twohearts.services

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.twohearts.database.*
import com.twohearts.models.TokenResponse
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.config.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.*

private val logger = KotlinLogging.logger {}

data class AuthUser(val id: String, val email: String)

class AuthService(
    config: ApplicationConfig,
    private val mailService: MailService
) {
    private val jwtSecret            = config.property("jwt.secret").getString()
    private val jwtIssuer            = config.property("jwt.issuer").getString()
    private val accessExpirySeconds  = config.property("jwt.accessExpirySeconds").getString().toLong()
    private val refreshExpirySeconds = config.property("jwt.refreshExpirySeconds").getString().toLong()
    private val magicLinkExpiryMin   = config.property("app.magicLinkExpiryMinutes").getString().toLong()
    private val algorithm            = Algorithm.HMAC256(jwtSecret)
    private val rng                  = SecureRandom()

    fun requestMagicLink(email: String) {
        val normalized = email.lowercase().trim()
        require(normalized.matches(Regex("^[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}$"))) {
            "Invalid email address"
        }
        val user = transaction { findOrCreateUser(normalized) }
        if (!user.isActive) return  // silencioso — evita enumeración

        val rawToken  = generateToken(48)
        val tokenHash = sha256(rawToken)
        // FIX ERROR 2: usar java.time.OffsetDateTime directamente (no kotlinx.datetime)
        val expiresAt = OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(magicLinkExpiryMin)

        transaction {
            MagicLinksTable.insert {
                it[userId]         = UUID.fromString(user.id)
                it[this.tokenHash] = tokenHash
                it[this.expiresAt] = expiresAt
                it[createdAt]      = OffsetDateTime.now(ZoneOffset.UTC)
            }
        }
        mailService.sendMagicLink(normalized, rawToken)
        logger.info { "Magic link issued for user ${user.id}" }
    }

    fun verifyMagicLink(rawToken: String, deviceId: String): TokenResponse {
        val tokenHash = sha256(rawToken)
        val userId = transaction {
            val row = MagicLinksTable
                .join(UsersTable, JoinType.INNER, MagicLinksTable.userId, UsersTable.id)
                .select(MagicLinksTable.id, MagicLinksTable.userId, MagicLinksTable.expiresAt,
                        MagicLinksTable.usedAt, UsersTable.isActive)
                .where { MagicLinksTable.tokenHash eq tokenHash }
                .singleOrNull()
                ?: throw IllegalArgumentException("Invalid or expired token")

            val expiresAt = row[MagicLinksTable.expiresAt]
            val usedAt    = row[MagicLinksTable.usedAt]
            val isActive  = row[UsersTable.isActive]

            if (usedAt != null || OffsetDateTime.now(ZoneOffset.UTC).isAfter(expiresAt) || !isActive)
                throw IllegalArgumentException("Invalid or expired token")

            // marcar como usado (atómico, single-use)
            MagicLinksTable.update({ MagicLinksTable.tokenHash eq tokenHash }) {
                it[this.usedAt] = OffsetDateTime.now(ZoneOffset.UTC)
            }
            row[MagicLinksTable.userId].value.toString()
        }
        return issueTokenPair(userId, deviceId)
    }

    fun refreshTokens(rawRefreshToken: String, deviceId: String): TokenResponse {
        val tokenHash = sha256(rawRefreshToken)
        val userId = transaction {
            val row = RefreshTokensTable
                .select(RefreshTokensTable.id, RefreshTokensTable.userId,
                        RefreshTokensTable.expiresAt, RefreshTokensTable.revokedAt,
                        RefreshTokensTable.deviceId)
                .where { RefreshTokensTable.tokenHash eq tokenHash }
                .singleOrNull()
                ?: throw IllegalArgumentException("Invalid refresh token")

            if (row[RefreshTokensTable.revokedAt] != null ||
                OffsetDateTime.now(ZoneOffset.UTC).isAfter(row[RefreshTokensTable.expiresAt]))
                throw IllegalArgumentException("Refresh token expired or revoked")

            val storedDev = row[RefreshTokensTable.deviceId]
            if (storedDev != null && storedDev != deviceId) {
                revokeAllTokensForUser(row[RefreshTokensTable.userId].value.toString())
                throw IllegalArgumentException("Token reuse from different device")
            }
            RefreshTokensTable.update({ RefreshTokensTable.tokenHash eq tokenHash }) {
                it[revokedAt] = OffsetDateTime.now(ZoneOffset.UTC)
            }
            row[RefreshTokensTable.userId].value.toString()
        }
        return issueTokenPair(userId, deviceId)
    }

    fun getUserById(userId: String): AuthUser? = transaction {
        UsersTable.select(UsersTable.id, UsersTable.email, UsersTable.isActive)
            .where { UsersTable.id eq UUID.fromString(userId) }
            .singleOrNull()
            ?.let { AuthUser(it[UsersTable.id].value.toString(), it[UsersTable.email]) }
    }

    private fun findOrCreateUser(email: String): ActiveUser {
        val existing = UsersTable.select(UsersTable.id, UsersTable.isActive)
            .where { UsersTable.email eq email }.singleOrNull()
        if (existing != null)
            return ActiveUser(existing[UsersTable.id].value.toString(), existing[UsersTable.isActive])

        val now = OffsetDateTime.now(ZoneOffset.UTC)
        val id = UsersTable.insertAndGetId {
            it[this.email] = email
            it[isActive]   = true
            it[createdAt]  = now
            it[updatedAt]  = now
        }
        return ActiveUser(id.value.toString(), true)
    }

    private fun issueTokenPair(userId: String, deviceId: String): TokenResponse {
        val now           = Date()
        val accessExpiry  = Date(now.time + accessExpirySeconds * 1000)
        val refreshExpiry = Date(now.time + refreshExpirySeconds * 1000)

        val accessToken = JWT.create()
            .withIssuer(jwtIssuer)
            .withSubject(userId)
            .withClaim("did", deviceId)
            .withJWTId(UUID.randomUUID().toString())
            .withIssuedAt(now)
            .withExpiresAt(accessExpiry)
            .sign(algorithm)

        val rawRefresh  = generateToken(64)
        val refreshHash = sha256(rawRefresh)
        transaction {
            RefreshTokensTable.insert {
                it[this.userId]   = UUID.fromString(userId)
                it[tokenHash]     = refreshHash
                it[this.deviceId] = deviceId
                it[expiresAt]     = OffsetDateTime.ofInstant(refreshExpiry.toInstant(), ZoneOffset.UTC)
                it[createdAt]     = OffsetDateTime.now(ZoneOffset.UTC)
            }
        }
        return TokenResponse(accessToken = accessToken, refreshToken = rawRefresh, userId = userId)
    }

    private fun revokeAllTokensForUser(userId: String) {
        RefreshTokensTable.update({ RefreshTokensTable.userId eq UUID.fromString(userId) }) {
            it[revokedAt] = OffsetDateTime.now(ZoneOffset.UTC)
        }
    }

    private fun generateToken(bytes: Int): String {
        val buf = ByteArray(bytes)
        rng.nextBytes(buf)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf)
    }

    private fun sha256(input: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }
}

private data class ActiveUser(val id: String, val isActive: Boolean)
