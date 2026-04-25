package com.appmaster.routes

import com.appmaster.routes.dto.AdminProfileDto
import com.appmaster.routes.dto.ApiResponse
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.adminMeRoutes() {
    authenticate("cf-access") {
        get("/api/v1/admin/me") {
            val principal = call.requireAdminPrincipal()
            call.respond(
                HttpStatusCode.OK,
                ApiResponse(
                    data = AdminProfileDto(
                        id = principal.adminId,
                        email = principal.email,
                        displayName = principal.displayName
                    )
                )
            )
        }
    }
}
