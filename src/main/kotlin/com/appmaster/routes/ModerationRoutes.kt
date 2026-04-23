package com.appmaster.routes

import com.appmaster.domain.error.DomainError
import com.appmaster.domain.error.DomainException
import com.appmaster.domain.model.`enum`.ModerationStatus
import com.appmaster.domain.model.valueobject.BestId
import com.appmaster.domain.model.valueobject.ThemeId
import com.appmaster.domain.usecase.moderation.GetPendingContentsUseCase
import com.appmaster.domain.usecase.moderation.ReviewBestUseCase
import com.appmaster.domain.usecase.moderation.ReviewThemeUseCase
import com.appmaster.routes.dto.ApiResponse
import com.appmaster.routes.dto.UpdateModerationStatusRequest
import com.appmaster.routes.dto.toDto
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

fun Route.moderationRoutes() {
    val getPendingContentsUseCase by inject<GetPendingContentsUseCase>()
    val reviewBestUseCase by inject<ReviewBestUseCase>()
    val reviewThemeUseCase by inject<ReviewThemeUseCase>()

    authenticate("admin") {
        route("/api/v1/admin/moderation") {

            get("/bests") {
                val statusParam = call.request.queryParameters["status"] ?: "pending"
                val status = ModerationStatus.fromId(statusParam)
                    ?: throw DomainException(DomainError.InvalidModerationStatus)
                val pagination = call.parsePagination()

                val bests = getPendingContentsUseCase.getBests(status, pagination.limit, pagination.offset)
                call.respond(ApiResponse(data = bests.map { it.toDto() }))
            }

            patch("/bests/{bestId}") {
                val bestId = call.parameters["bestId"]!!
                val request = call.receive<UpdateModerationStatusRequest>()
                val status = ModerationStatus.fromId(request.status)
                    ?: throw DomainException(DomainError.InvalidModerationStatus)

                val best = reviewBestUseCase(
                    ReviewBestUseCase.Params(
                        bestId = BestId(bestId),
                        status = status
                    )
                )
                call.respond(ApiResponse(data = best.toDto()))
            }

            get("/themes") {
                val statusParam = call.request.queryParameters["status"] ?: "pending"
                val status = ModerationStatus.fromId(statusParam)
                    ?: throw DomainException(DomainError.InvalidModerationStatus)
                val pagination = call.parsePagination()

                val themes = getPendingContentsUseCase.getThemes(status, pagination.limit, pagination.offset)
                call.respond(ApiResponse(data = themes.map { it.toDto() }))
            }

            patch("/themes/{themeId}") {
                val themeId = call.parameters["themeId"]!!
                val request = call.receive<UpdateModerationStatusRequest>()
                val status = ModerationStatus.fromId(request.status)
                    ?: throw DomainException(DomainError.InvalidModerationStatus)

                val theme = reviewThemeUseCase(
                    ReviewThemeUseCase.Params(
                        themeId = ThemeId(themeId),
                        status = status
                    )
                )
                call.respond(ApiResponse(data = theme.toDto()))
            }
        }
    }
}
