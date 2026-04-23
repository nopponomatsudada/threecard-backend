package com.appmaster.routes

import com.appmaster.domain.model.valueobject.ThemeId
import com.appmaster.domain.usecase.theme.CreateThemeUseCase
import com.appmaster.domain.usecase.theme.GetThemeDetailUseCase
import com.appmaster.domain.usecase.theme.GetThemesUseCase
import com.appmaster.routes.dto.ApiResponse
import com.appmaster.routes.dto.CreateThemeRequest
import com.appmaster.routes.dto.toDto
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

fun Route.themeRoutes() {
    val getThemesUseCase by inject<GetThemesUseCase>()
    val createThemeUseCase by inject<CreateThemeUseCase>()
    val getThemeDetailUseCase by inject<GetThemeDetailUseCase>()

    authenticate("jwt") {
        rateLimit(RateLimitName("api")) {
        route("/api/v1/themes") {

            get {
                val tagId = call.request.queryParameters["tagId"]
                val areaCode = call.request.queryParameters["areaCode"]
                val pagination = call.parsePagination()

                val themes = getThemesUseCase(
                    GetThemesUseCase.Params(
                        tagId = tagId,
                        areaCode = areaCode,
                        limit = pagination.limit,
                        offset = pagination.offset,
                    )
                )
                call.respond(ApiResponse(data = themes.map { (theme, bestCount) -> theme.toDto(bestCount) }))
            }

            post {
                val userId = call.requireUserId()

                val request = call.receive<CreateThemeRequest>()
                val theme = createThemeUseCase(
                    CreateThemeUseCase.Params(
                        title = request.title,
                        description = request.description,
                        tagId = request.tagId,
                        areaCode = request.areaCode,
                        authorId = userId
                    )
                )
                call.respond(HttpStatusCode.Created, ApiResponse(data = theme.toDto()))
            }

            get("/{themeId}") {
                val themeIdStr = call.parameters["themeId"]!!
                val (theme, bestCount) = getThemeDetailUseCase(ThemeId(themeIdStr))
                call.respond(ApiResponse(data = theme.toDto(bestCount)))
            }
        }
        }
    }
}
