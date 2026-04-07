package com.appmaster.routes

import com.appmaster.domain.usecase.discover.GetRandomCardsUseCase
import com.appmaster.routes.dto.ApiResponse
import com.appmaster.routes.dto.toDto
import io.ktor.server.auth.*
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

fun Route.discoverRoutes() {
    val getRandomCardsUseCase by inject<GetRandomCardsUseCase>()

    authenticate("jwt") {
        rateLimit(RateLimitName("api")) {
            get("/api/v1/discover") {
                val userId = call.requireUserId()
                val tagId = call.request.queryParameters["tagId"]

                val cards = getRandomCardsUseCase(
                    GetRandomCardsUseCase.Params(userId = userId, tagId = tagId)
                )
                call.respond(ApiResponse(data = cards.map { it.toDto() }))
            }
        }
    }
}
