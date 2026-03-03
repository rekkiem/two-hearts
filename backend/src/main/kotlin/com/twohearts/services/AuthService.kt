package com.twohearts.services

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.twohearts.database.*
import com.twohearts.models.TokenResponse
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.config.*
import kotlinx.datetime.*
import kotlinx.datetime.TimeZone
import kotlin.time.Duration.Companion.minutes
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.kotlin.datetime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.sql.transactions.transaction
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.*

private val logger = KotlinLogging.logger {}

data class AuthUser(val id: String, val email: String)

class AuthService(
    config: ApplicationConfig,
    private val mailService: MailService
) {
    private val jwtSecret         = config.property("jwt.secret").getString()
    private val jwtIssuer         = config.property("jwt.issuer").getString()
    private val accessExpirySeconds  = config.property("jwt.accessExpirySeconds").getString().toLong()
    private val refreshExpirySeconds = config.property("jwt.refreshExpirySeconds").getString().toLong()
    private val magicLinkExpiry   = config.property("app.magicLinkExpiryMinutes").getString().toLong()
    private val algorithm         = Algorithm.HMAC256(jwtSecret)
    private val rng               = SecureRandom()

    // ---- Public API ----

    fun requestMagicLink(email: String) {
        val normalizedEmail = email.lowercase().trim()
        require(normalizedEmail.matches(Regex("^[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}$"))) {
            "Invalid email address"
        }

        val user = transaction { findOrCreateUser(normalizedEmail) }
        if (!user.isActive) {
            logger.warn { "Magic link requested for inactive user ${user.id}" }
            return  // Silently ignore — prevents enumeration
        }

        val rawToken = generateToken(48)
        val tokenHash = sha256(rawToken)
        val expiresAt = Clock.System.now().plus(magicLinkExpiry.minutes)

        transaction {
            MagicLinksTable.insert {
                it[userId]         = UUID.fromString(user.id)
                it[this.tokenHash] = tokenHash
                it[this.expiresAt] = expiresAt
                it[createdAt]      = Clock.System.now()
            }
        }

        mailService.sendMagicLink(normalizedEmail, rawToken)
        logger.info { "Magic link issued for user ${user.id}" }
    }

    fun verifyMagicLink(rawToken: String, deviceId: String): TokenResponse {
        val tokenHash = sha256(rawToken)

        val (userId, email) = transaction {
            val row = MagicLinksTable
                .join(UsersTable, JoinType.INNER, MagicLinksTable.userId, UsersTable.id)
                .select(MagicLinksTable.id, MagicLinksTable.userId, MagicLinksTable.expiresAt,
                        MagicLinksTable.usedAt, UsersTable.email, UsersTable.isActive)
                .where { MagicLinksTable.tokenHash eq tokenHash }
                .singleOrNull()
                ?: throw IllegalArgumentException("Invalid or expired token")

            val expiresAt = row[MagicLinksTable.expiresAt]
            val usedAt    = row[MagicLinksTable.usedAt]
            val isActive  = row[UsersTable.isActive]

            if (usedAt != null || Clock.System.now() > expiresAt || !isActive) {
                throw IllegalArgumentException("Invalid or expired token")
            }

            // Mark as used (atomic single-use)
            MagicLinksTable.update({ MagicLinksTable.tokenHash eq tokenHash }) {
                it[this.usedAt] = Clock.System.now()
            }

            row[MagicLinksTable.userId].value.toString() to row[UsersTable.email]
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

            val expiresAt  = row[RefreshTokensTable.expiresAt]
            val revokedAt  = row[RefreshTokensTable.revokedAt]
            val storedDev  = row[RefreshTokensTable.deviceId]

            if (revokedAt != null || Clock.System.now() > expiresAt) {
                throw IllegalArgumentException("Refresh token expired or revoked")
            }
            if (storedDev != null && storedDev != deviceId) {
                // Token reuse from different device — possible theft, revoke all
                RevocationService.revokeAllForUser(row[RefreshTokensTable.userId].value.toString())
                throw IllegalArgumentException("Token reuse detected")
            }

            // Revoke old token
            RefreshTokensTable.update({ RefreshTokensTable.tokenHash eq tokenHash }) {
                it[revokedAt] = Clock.System.now()
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

    // ---- Private helpers ----

    private fun findOrCreateUser(email: String): ActiveUser {
        val existing = UsersTable
            .select(UsersTable.id, UsersTable.isActive)
            .where { UsersTable.email eq email }
            .singleOrNull()

        if (existing != null) {
            return ActiveUser(
                id = existing[UsersTable.id].value.toString(),
                isActive = existing[UsersTable.isActive]
            )
        }

        val id = UsersTable.insertAndGetId {
            it[this.email]    = email
            it[isActive]      = true
            it[createdAt]     = Clock.System.now()
            it[updatedAt]     = Clock.System.now()
        }
        return ActiveUser(id = id.value.toString(), isActive = true)
    }

    private fun issueTokenPair(userId: String, deviceId: String): TokenResponse {
        val now         = Date()
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

        val rawRefresh = generateToken(64)
        val refreshHash = sha256(rawRefresh)

        val refreshExpiryInstant = kotlinx.datetime.Instant.fromEpochMilliseconds(refreshExpiry.time)
        transaction {
            RefreshTokensTable.insert {
                it[this.userId]    = UUID.fromString(userId)
                it[tokenHash]      = refreshHash
                it[this.deviceId]  = deviceId
                it[expiresAt]      = refreshExpiryInstant
                it[createdAt]      = Clock.System.now()
            }
        }

        return TokenResponse(
            accessToken  = accessToken,
            refreshToken = rawRefresh,
            userId       = userId
        )
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

object RevocationService {
    fun revokeAllForUser(userId: String) = transaction {
        RefreshTokensTable.update({ RefreshTokensTable.userId eq UUID.fromString(userId) }) {
            it[revokedAt] = Clock.System.now()
        }
    }
}

