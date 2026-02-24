package com.twohearts.routes

import com.twohearts.models.*
import com.twohearts.services.AuthService
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.authRoutes(authService: AuthService) {
    route("/auth") {

        // POST /api/v1/auth/magic-link
        post("/magic-link") {
            val req = call.receive<MagicLinkRequest>()
            authService.requestMagicLink(req.email)
            call.respond(HttpStatusCode.OK, MagicLinkResponse(
                "If that email exists, a login link has been sent. Check Mailhog at http://localhost:8025"
            ))
        }

        // POST /api/v1/auth/verify
        post("/verify") {
            val req = call.receive<VerifyTokenRequest>()
            val tokens = authService.verifyMagicLink(req.token, req.deviceId)
            call.respond(HttpStatusCode.OK, tokens)
        }

        // GET /api/v1/auth/verify-web?token=... (browser deep link for local testing)
        get("/verify-web") {
            val token = call.request.queryParameters["token"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing token"))
            try {
                val tokens = authService.verifyMagicLink(token, "web")
                // Return a simple HTML page with the token (for dev only)
                call.respondText(
                    """
                    <!DOCTYPE html><html><body style="font-family:sans-serif;max-width:480px;margin:auto;padding:32px">
                    <h2>✅ Signed in to TwoHearts!</h2>
                    <p>Copy your access token to use in the app or API calls:</p>
                    <textarea rows="4" style="width:100%;font-family:monospace;font-size:12px">${tokens.accessToken}</textarea>
                    <br><br>
                    <p>Refresh token:</p>
                    <textarea rows="2" style="width:100%;font-family:monospace;font-size:12px">${tokens.refreshToken}</textarea>
                    <p><strong>userId:</strong> ${tokens.userId}</p>
                    </body></html>
                    """.trimIndent(),
                    ContentType.Text.Html
                )
            } catch (e: Exception) {
                call.respondText(
                    "<html><body><h2>❌ Invalid or expired token</h2></body></html>",
                    ContentType.Text.Html,
                    HttpStatusCode.BadRequest
                )
            }
        }

        // POST /api/v1/auth/refresh
        post("/refresh") {
            val req = call.receive<RefreshRequest>()
            val tokens = authService.refreshTokens(req.refreshToken, req.deviceId)
            call.respond(HttpStatusCode.OK, tokens)
        }

        // POST /api/v1/auth/logout (requires auth)
        authenticate("auth-jwt") {
            post("/logout") {
                // Refresh token revocation happens client-side (delete stored token)
                // For stateless JWT, client simply discards access token
                call.respond(HttpStatusCode.OK, mapOf("message" to "Logged out"))
            }

            get("/me") {
                val principal = call.principal<JWTPrincipal>()!!
                val userId = principal.payload.subject
                val user = authService.getUserById(userId)
                    ?: return@get call.respond(HttpStatusCode.NotFound, ErrorResponse("User not found"))
                call.respond(HttpStatusCode.OK, mapOf("userId" to user.id, "email" to user.email))
            }
        }
    }
}
