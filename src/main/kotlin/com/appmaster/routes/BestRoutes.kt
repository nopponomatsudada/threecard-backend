package com.appmaster.routes

import com.appmaster.domain.model.valueobject.ThemeId
import com.appmaster.domain.usecase.best.GetBestsByThemeUseCase
import com.appmaster.domain.usecase.best.PostBestUseCase
import com.appmaster.routes.dto.ApiResponse
import com.appmaster.routes.dto.PostBestRequest
import com.appmaster.routes.dto.toDto
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

fun Route.bestRoutes() {
    val postBestUseCase by inject<PostBestUseCase>()
    val getBestsByThemeUseCase by inject<GetBestsByThemeUseCase>()

    authenticate("jwt") {
        rateLimit(RateLimitName("api")) {
        route("/api/v1/themes/{themeId}/bests") {

            get {
                val themeId = ThemeId(call.parameters["themeId"]!!)
                val pagination = call.parsePagination()

                val bests = getBestsByThemeUseCase(
                    GetBestsByThemeUseCase.Params(
                        themeId = themeId,
                        limit = pagination.limit,
                        offset = pagination.offset,
                    )
                )
                call.respond(ApiResponse(data = bests.map { it.toDto() }))
            }

            post {
                val userId = call.requireUserId()
                val themeId = ThemeId(call.parameters["themeId"]!!)
                val request = call.receive<PostBestRequest>()

                val best = postBestUseCase(
                    PostBestUseCase.Params(
                        themeId = themeId,
                        authorId = userId,
                        items = request.items.map { item ->
                            PostBestUseCase.ItemParam(
                                rank = item.rank,
                                name = item.name,
                                description = item.description
                            )
                        }
                    )
                )
                call.respond(HttpStatusCode.Created, ApiResponse(data = best.toDto()))
            }
        }
        }
    }
}
