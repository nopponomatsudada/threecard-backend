@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.appmaster.routes

import com.appmaster.domain.usecase.best.GetMyBestsUseCase
import com.appmaster.domain.usecase.user.GetMyProfileUseCase
import com.appmaster.routes.dto.ApiResponse
import com.appmaster.routes.dto.toDto
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

fun Route.userRoutes() {
    val getMyProfileUseCase by inject<GetMyProfileUseCase>()
    val getMyBestsUseCase by inject<GetMyBestsUseCase>()

    authenticate("jwt") {
        route("/api/v1/users") {
            get("/me") {
                val userId = call.requireUserId()
                val profile = getMyProfileUseCase(userId)
                call.respond(ApiResponse(data = profile.toDto()))
            }

            get("/me/bests") {
                val userId = call.requireUserId()
                val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 20
                val offset = call.request.queryParameters["offset"]?.toIntOrNull() ?: 0

                val bests = getMyBestsUseCase(
                    GetMyBestsUseCase.Params(authorId = userId, limit = limit, offset = offset)
                )
                call.respond(ApiResponse(data = bests.map { it.toDto() }))
            }
        }
    }
}
