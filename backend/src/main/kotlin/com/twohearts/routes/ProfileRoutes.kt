package com.twohearts.routes

import com.twohearts.models.*
import com.twohearts.services.ProfileService
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.profileRoutes(profileService: ProfileService) {
    route("/profiles") {

        // GET /api/v1/profiles/me
        get("/me") {
            val userId = call.principal<JWTPrincipal>()!!.payload.subject
            val profile = profileService.getProfile(userId)
                ?: return@get call.respond(HttpStatusCode.NotFound, ErrorResponse("Profile not created yet"))
            call.respond(HttpStatusCode.OK, profile)
        }

        // POST /api/v1/profiles (create or update)
        post {
            val userId = call.principal<JWTPrincipal>()!!.payload.subject
            val req = call.receive<CreateProfileRequest>()
            val profile = profileService.upsertProfile(userId, req)
            call.respond(HttpStatusCode.OK, profile)
        }

        // PUT /api/v1/profiles (alias for POST)
        put {
            val userId = call.principal<JWTPrincipal>()!!.payload.subject
            val req = call.receive<CreateProfileRequest>()
            val profile = profileService.upsertProfile(userId, req)
            call.respond(HttpStatusCode.OK, profile)
        }

        // POST /api/v1/profiles/photo (multipart upload)
        post("/photo") {
            val userId = call.principal<JWTPrincipal>()!!.payload.subject

            var photoBytes: ByteArray? = null
            var contentType = "image/jpeg"

            val multipart = call.receiveMultipart()
            multipart.forEachPart { part ->
                when (part) {
                    is PartData.FileItem -> {
                        if (photoBytes == null) {
                            photoBytes = part.streamProvider().readBytes()
                            contentType = part.contentType?.toString() ?: "image/jpeg"
                        }
                    }
                    else -> {}
                }
                part.dispose()
            }

            val bytes = photoBytes
                ?: return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("No photo uploaded"))

            if (bytes.size > 5 * 1024 * 1024) {
                return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Photo too large (max 5MB)"))
            }

            val url = profileService.updatePhoto(userId, bytes, contentType)
            call.respond(HttpStatusCode.OK, PhotoUploadResponse(url))
        }

        // GET /api/v1/profiles/{userId} (public profile of a matched user)
        get("/{userId}") {
            val requesterId  = call.principal<JWTPrincipal>()!!.payload.subject
            val targetUserId = call.parameters["userId"]!!
            val profile = profileService.getProfile(targetUserId)
                ?: return@get call.respond(HttpStatusCode.NotFound, ErrorResponse("Profile not found"))
            call.respond(HttpStatusCode.OK, profile)
        }
    }
}
