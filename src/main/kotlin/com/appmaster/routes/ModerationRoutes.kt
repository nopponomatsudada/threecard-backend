package com.appmaster.routes

import com.appmaster.domain.error.DomainError
import com.appmaster.domain.error.DomainException
import com.appmaster.domain.model.`enum`.ModerationAction
import com.appmaster.domain.model.`enum`.ModerationStatus
import com.appmaster.domain.model.valueobject.BestId
import com.appmaster.domain.model.valueobject.ThemeId
import com.appmaster.domain.usecase.moderation.GetPendingContentsUseCase
import com.appmaster.domain.usecase.moderation.GetModerationAuditLogsUseCase
import com.appmaster.domain.usecase.moderation.ReviewBestUseCase
import com.appmaster.domain.usecase.moderation.ReviewThemeUseCase
import com.appmaster.domain.usecase.moderation.SkipBestUseCase
import com.appmaster.domain.usecase.moderation.SkipThemeUseCase
import com.appmaster.routes.dto.ApiResponse
import com.appmaster.routes.dto.SkipModerationRequest
import com.appmaster.routes.dto.UpdateModerationStatusRequest
import com.appmaster.routes.dto.toDto
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.callid.*
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject
import org.slf4j.LoggerFactory

private val adminAuditLog = LoggerFactory.getLogger("com.appmaster.audit.admin")

fun Route.moderationRoutes() {
    val getPendingContentsUseCase by inject<GetPendingContentsUseCase>()
    val getModerationAuditLogsUseCase by inject<GetModerationAuditLogsUseCase>()
    val reviewBestUseCase by inject<ReviewBestUseCase>()
    val reviewThemeUseCase by inject<ReviewThemeUseCase>()
    val skipBestUseCase by inject<SkipBestUseCase>()
    val skipThemeUseCase by inject<SkipThemeUseCase>()

    authenticate("cf-access") {
        rateLimit(RateLimitName("api")) {
            route("/api/v1/admin/moderation") {
                get("/bests") {
                    val statusParam = call.request.queryParameters["status"] ?: "pending"
                    val status = ModerationStatus.fromId(statusParam)
                        ?: throw DomainException(DomainError.InvalidModerationStatus)
                    val pagination = call.parsePagination()

                    val bests = getPendingContentsUseCase.getBests(status, pagination.limit, pagination.offset)
                    call.respond(ApiResponse(data = bests.map { it.toDto() }))
                }

                get("/themes") {
                    val statusParam = call.request.queryParameters["status"] ?: "pending"
                    val status = ModerationStatus.fromId(statusParam)
                        ?: throw DomainException(DomainError.InvalidModerationStatus)
                    val pagination = call.parsePagination()

                    val themes = getPendingContentsUseCase.getThemes(status, pagination.limit, pagination.offset)
                    call.respond(ApiResponse(data = themes.map { it.toDto() }))
                }
            }

            route("/api/v1/admin/audit-logs") {
                get {
                    val pagination = call.parsePagination()
                    val action = call.request.queryParameters["action"]?.let { actionId ->
                        ModerationAction.fromId(actionId)
                            ?: throw DomainException(DomainError.ValidationError("action is invalid"))
                    }
                    val logs = getModerationAuditLogsUseCase(pagination.limit, pagination.offset, action)
                    call.respond(ApiResponse(data = logs.map { it.toDto() }))
                }
            }
        }

        rateLimit(RateLimitName("sensitive")) {
            route("/api/v1/admin/moderation") {
                patch("/bests/{bestId}") {
                    val bestId = call.parameters["bestId"]!!
                    val request = call.receive<UpdateModerationStatusRequest>()
                    val status = ModerationStatus.fromId(request.status)
                        ?: throw DomainException(DomainError.InvalidModerationStatus)

                    val best = reviewBestUseCase(
                        ReviewBestUseCase.Params(
                            bestId = BestId(bestId),
                            status = status,
                            reviewer = call.requireAdminPrincipal().label,
                            note = request.note
                        )
                    )
                    adminAuditLog.info("event=moderation.best.review bestId=$bestId status=${status.id} remoteHost=${call.request.origin.remoteHost} requestId=${call.callId}")
                    call.respond(ApiResponse(data = best.toDto()))
                }

                post("/bests/{bestId}/skip") {
                    val bestId = call.parameters["bestId"]!!
                    val request = call.receiveSkipModerationRequest()

                    val best = skipBestUseCase(
                        SkipBestUseCase.Params(
                            bestId = BestId(bestId),
                            reviewer = call.requireAdminPrincipal().label,
                            note = request.note
                        )
                    )
                    adminAuditLog.info("event=moderation.best.skip bestId=$bestId remoteHost=${call.request.origin.remoteHost} requestId=${call.callId}")
                    call.respond(ApiResponse(data = best.toDto()))
                }

                patch("/themes/{themeId}") {
                    val themeId = call.parameters["themeId"]!!
                    val request = call.receive<UpdateModerationStatusRequest>()
                    val status = ModerationStatus.fromId(request.status)
                        ?: throw DomainException(DomainError.InvalidModerationStatus)

                    val theme = reviewThemeUseCase(
                        ReviewThemeUseCase.Params(
                            themeId = ThemeId(themeId),
                            status = status,
                            reviewer = call.requireAdminPrincipal().label,
                            note = request.note
                        )
                    )
                    adminAuditLog.info("event=moderation.theme.review themeId=$themeId status=${status.id} remoteHost=${call.request.origin.remoteHost} requestId=${call.callId}")
                    call.respond(ApiResponse(data = theme.toDto()))
                }

                post("/themes/{themeId}/skip") {
                    val themeId = call.parameters["themeId"]!!
                    val request = call.receiveSkipModerationRequest()

                    val theme = skipThemeUseCase(
                        SkipThemeUseCase.Params(
                            themeId = ThemeId(themeId),
                            reviewer = call.requireAdminPrincipal().label,
                            note = request.note
                        )
                    )
                    adminAuditLog.info("event=moderation.theme.skip themeId=$themeId remoteHost=${call.request.origin.remoteHost} requestId=${call.callId}")
                    call.respond(ApiResponse(data = theme.toDto()))
                }
            }
        }
    }
}

private suspend fun RoutingCall.receiveSkipModerationRequest(): SkipModerationRequest {
    val contentType = request.headers[HttpHeaders.ContentType]
    if (contentType == null || request.contentLength() == 0L) {
        return SkipModerationRequest()
    }
    return receiveNullable<SkipModerationRequest>() ?: SkipModerationRequest()
}
