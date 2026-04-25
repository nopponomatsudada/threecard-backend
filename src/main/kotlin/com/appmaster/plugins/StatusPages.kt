package com.appmaster.plugins

import com.appmaster.domain.error.DomainError
import com.appmaster.domain.error.DomainException
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.callid.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory

@Serializable
data class ErrorResponse(
    val error: ErrorDetail
)

@Serializable
data class ErrorDetail(
    val code: String,
    val message: String,
    val details: Map<String, String>? = null
)

internal suspend fun ApplicationCall.respondUnauthorized(message: String = DomainError.Unauthorized.message) {
    respond(
        HttpStatusCode.Unauthorized,
        ErrorResponse(error = ErrorDetail(code = DomainError.Unauthorized.code, message = message))
    )
}

private val errorLog = LoggerFactory.getLogger("com.appmaster.errors")

fun Application.configureStatusPages() {
    install(StatusPages) {
        exception<DomainException> { call, cause ->
            val (statusCode, response) = mapDomainError(cause.error)
            call.respond(statusCode, response)
        }

        exception<Throwable> { call, cause ->
            // Log a single concise line at ERROR; full stack only at DEBUG so we
            // don't leak request payloads to production log sinks.
            errorLog.error(
                "Unhandled exception class={} msg={} requestId={}",
                cause::class.simpleName,
                cause.message,
                call.callId
            )
            errorLog.debug("Unhandled exception stack", cause)
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponse(
                    error = ErrorDetail(
                        code = DomainError.ServerError.code,
                        message = DomainError.ServerError.message
                    )
                )
            )
        }

        status(HttpStatusCode.NotFound) { call, status ->
            call.respond(
                status,
                ErrorResponse(
                    error = ErrorDetail(
                        code = DomainError.NotFound("リソース").code,
                        message = "リソースが見つかりません"
                    )
                )
            )
        }

        status(HttpStatusCode.Unauthorized) { call, status ->
            call.respond(
                status,
                ErrorResponse(
                    error = ErrorDetail(
                        code = DomainError.Unauthorized.code,
                        message = DomainError.Unauthorized.message
                    )
                )
            )
        }
    }
}

private fun mapDomainError(error: DomainError): Pair<HttpStatusCode, ErrorResponse> {
    val statusCode = when (error) {
        is DomainError.NotFound -> HttpStatusCode.NotFound
        DomainError.Forbidden -> HttpStatusCode.Forbidden
        DomainError.Unauthorized,
        DomainError.InvalidCredentials,
        DomainError.InvalidDeviceCredentials,
        DomainError.InvalidRefreshToken -> HttpStatusCode.Unauthorized
        DomainError.EmailAlreadyExists,
        DomainError.AlreadyPosted,
        DomainError.DuplicateBookmark -> HttpStatusCode.Conflict
        DomainError.ServerError,
        DomainError.NetworkError -> HttpStatusCode.InternalServerError
        DomainError.ThemeTitleTooLong,
        DomainError.ThemeDescriptionTooLong,
        DomainError.BestItemNameRequired,
        DomainError.BestItemNameTooLong,
        DomainError.BestItemDescriptionTooLong,
        DomainError.TagNotSelected,
        DomainError.InvalidModerationStatus,
        is DomainError.ValidationError -> HttpStatusCode.BadRequest
    }
    return statusCode to ErrorResponse(
        error = ErrorDetail(code = error.code, message = error.message)
    )
}
