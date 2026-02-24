package com.twohearts.routes

import com.twohearts.models.*
import com.twohearts.services.MatchingService
import io.ktor.http.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.matchingRoutes(matchingService: MatchingService) {
    route("/matches") {

        // GET /api/v1/matches — today's matches (generate if not yet)
        get {
            val userId = call.principal<JWTPrincipal>()!!.payload.subject
            val matches = matchingService.getTodayMatches(userId)
            call.respond(HttpStatusCode.OK, matches)
        }

        // POST /api/v1/matches/{matchId}/interact
        post("/{matchId}/interact") {
            val userId  = call.principal<JWTPrincipal>()!!.payload.subject
            val matchId = call.parameters["matchId"]!!
            val req     = call.receive<MatchInteractionRequest>()

            val result = matchingService.interactWithMatch(userId, matchId, req.action)
            call.respond(HttpStatusCode.OK, result)
        }
    }
}

fun Route.intentRoutes(matchingService: MatchingService) {
    route("/intents") {

        // GET /api/v1/intents/question — today's question
        get("/question") {
            val question = matchingService.getTodayQuestion()
            call.respond(HttpStatusCode.OK, question)
        }

        // GET /api/v1/intents/today — current user's intent for today
        get("/today") {
            val userId = call.principal<JWTPrincipal>()!!.payload.subject
            val intent = matchingService.getTodayIntent(userId)
            if (intent != null) {
                call.respond(HttpStatusCode.OK, intent)
            } else {
                call.respond(HttpStatusCode.NoContent)
            }
        }

        // POST /api/v1/intents — submit today's intent
        post {
            val userId = call.principal<JWTPrincipal>()!!.payload.subject
            val req    = call.receive<SubmitIntentRequest>()
            val intent = matchingService.submitIntent(userId, req.questionId, req.answer)
            call.respond(HttpStatusCode.Created, intent)
        }
    }
}
