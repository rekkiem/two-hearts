package com.twohearts

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.twohearts.database.DatabaseFactory
import com.twohearts.routes.*
import com.twohearts.services.*
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import kotlinx.serialization.json.Json
import java.time.Duration

private val logger = KotlinLogging.logger {}

fun main(args: Array<String>): Unit = EngineMain.main(args)

fun Application.module() {
    val dbUrl      = environment.config.property("database.url").getString()
    val dbUser     = environment.config.property("database.user").getString()
    val dbPassword = environment.config.property("database.password").getString()
    val jwtSecret  = environment.config.property("jwt.secret").getString()
    val jwtIssuer  = environment.config.property("jwt.issuer").getString()

    // ---- Database ----
    DatabaseFactory.init(dbUrl, dbUser, dbPassword)
    logger.info { "Database connected and migrations applied" }

    // ---- Services ----
    val mailService     = MailService(environment.config)
    val minioService    = MinioService(environment.config)
    val embeddingService = EmbeddingService()
    val authService     = AuthService(environment.config, mailService)
    val profileService  = ProfileService(embeddingService, minioService)
    val matchingService = MatchingService(embeddingService)
    val chatService     = ChatService()

    // ---- Plugins ----
    install(ContentNegotiation) {
        json(Json {
            ignoreUnknownKeys = true
            isLenient = false
            prettyPrint = false
        })
    }

    install(WebSockets) {
        pingPeriod = Duration.ofSeconds(30)
        timeout = Duration.ofSeconds(60)
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }

    install(CORS) {
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Patch)
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        anyHost()
    }

    install(Authentication) {
        jwt("auth-jwt") {
            realm = "twohearts"
            verifier(
                JWT.require(Algorithm.HMAC256(jwtSecret))
                    .withIssuer(jwtIssuer)
                    .build()
            )
            validate { credential ->
                val userId = credential.payload.getClaim("sub").asString()
                if (!userId.isNullOrBlank()) JWTPrincipal(credential.payload) else null
            }
            challenge { _, _ ->
                call.respond(
                    HttpStatusCode.Unauthorized,
                    mapOf("error" to "Invalid or expired token")
                )
            }
        }
    }

    install(StatusPages) {
        exception<IllegalArgumentException> { call, cause ->
            logger.warn { "Bad request: ${cause.message}" }
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to (cause.message ?: "Bad request")))
        }
        exception<NotFoundException> { call, cause ->
            call.respond(HttpStatusCode.NotFound, mapOf("error" to (cause.message ?: "Not found")))
        }
        exception<ForbiddenException> { call, cause ->
            call.respond(HttpStatusCode.Forbidden, mapOf("error" to (cause.message ?: "Forbidden")))
        }
        exception<Throwable> { call, cause ->
            logger.error(cause) { "Unhandled error: ${cause.message}" }
            call.respond(
                HttpStatusCode.InternalServerError,
                mapOf("error" to "Internal server error")
            )
        }
    }

    routing {
        // Health check (no auth)
        get("/health") {
            call.respond(mapOf("status" to "ok", "service" to "twohearts-backend"))
        }

        route("/api/v1") {
            authRoutes(authService)

            authenticate("auth-jwt") {
                profileRoutes(profileService)
                matchingRoutes(matchingService)
                chatRoutes(chatService)
                intentRoutes(matchingService)
            }
        }

        // WebSocket - token passed as query param (mobile WebSocket limitation)
        webSocketRoutes(chatService, jwtSecret, jwtIssuer)
    }
}

class NotFoundException(message: String) : Exception(message)
class ForbiddenException(message: String) : Exception(message)
