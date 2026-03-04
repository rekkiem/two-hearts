package com.twohearts.routes

import com.twohearts.models.*
import com.twohearts.services.MatchingService
import io.ktor.http.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.intentRoutes(matchingService: MatchingService) {
    route("/intents") {
        get("/question") {
            call.respond(HttpStatusCode.OK, matchingService.getTodayQuestion())
        }
        get("/today") {
            val userId = call.principal<JWTPrincipal>()!!.payload.subject
            val intent = matchingService.getTodayIntent(userId)
            if (intent != null) call.respond(HttpStatusCode.OK, intent)
            else call.respond(HttpStatusCode.NoContent)
        }
        post {
            val userId = call.principal<JWTPrincipal>()!!.payload.subject
            val req    = call.receive<SubmitIntentRequest>()
            val intent = matchingService.submitIntent(userId, req.questionId, req.answer)
            call.respond(HttpStatusCode.Created, intent)
        }
    }
}
