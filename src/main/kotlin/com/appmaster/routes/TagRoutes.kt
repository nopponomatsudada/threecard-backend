package com.appmaster.routes

import com.appmaster.routes.dto.ApiResponse
import com.appmaster.routes.dto.allTagDtos
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.tagRoutes() {
    route("/api/v1/tags") {
        get {
            call.respond(ApiResponse(data = allTagDtos()))
        }
    }
}
