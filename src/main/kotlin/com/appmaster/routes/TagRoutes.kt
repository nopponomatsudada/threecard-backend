package com.appmaster.routes

import com.appmaster.routes.dto.ApiResponse
import com.appmaster.routes.dto.allTagDtos
import io.ktor.server.auth.*
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.tagRoutes() {
    authenticate("jwt") {
        rateLimit(RateLimitName("api")) {
            route("/api/v1/tags") {
                get {
                    call.respond(ApiResponse(data = allTagDtos()))
                }
            }
        }
    }
}
