@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.appmaster.routes

import com.appmaster.domain.usecase.best.GetMyBestsUseCase
import com.appmaster.domain.usecase.user.GetMyProfileUseCase
import com.appmaster.routes.dto.ApiResponse
import com.appmaster.routes.dto.toDto
import io.ktor.server.auth.*
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

fun Route.userRoutes() {
    val getMyProfileUseCase by inject<GetMyProfileUseCase>()
    val getMyBestsUseCase by inject<GetMyBestsUseCase>()

    authenticate("jwt") {
        rateLimit(RateLimitName("api")) {
            route("/api/v1/users") {
                get("/me") {
                    val userId = call.requireUserId()
                    val profile = getMyProfileUseCase(userId)
                    call.respond(ApiResponse(data = profile.toDto()))
                }

                get("/me/bests") {
                    val userId = call.requireUserId()
                    val pagination = call.parsePagination()

                    val bests = getMyBestsUseCase(
                        GetMyBestsUseCase.Params(
                            authorId = userId,
                            limit = pagination.limit,
                            offset = pagination.offset,
                        )
                    )
                    call.respond(ApiResponse(data = bests.map { it.toDto() }))
                }
            }
        }
    }
}
