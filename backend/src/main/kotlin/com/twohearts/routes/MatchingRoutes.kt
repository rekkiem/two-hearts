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
        get {
            val userId = call.principal<JWTPrincipal>()!!.payload.subject
            call.respond(HttpStatusCode.OK, matchingService.getTodayMatches(userId))
        }
        post("/{matchId}/interact") {
            val userId  = call.principal<JWTPrincipal>()!!.payload.subject
            val matchId = call.parameters["matchId"]!!
            val req     = call.receive<MatchInteractionRequest>()
            call.respond(HttpStatusCode.OK, matchingService.interactWithMatch(userId, matchId, req.action))
        }
    }
}
