package com.appmaster.routes

import com.appmaster.domain.error.DomainError
import com.appmaster.domain.error.DomainException
import io.ktor.server.routing.*

/**
 * Parsed pagination parameters.
 */
data class Pagination(val limit: Int, val offset: Int)

/**
 * Parse and validate `limit` / `offset` query parameters.
 *
 * Throws DomainException(ValidationError) when values are out of range or not numeric.
 * Matches the bounds declared in api-spec/openapi.yml (limit: 1..50, offset: >= 0).
 */
fun RoutingCall.parsePagination(
    defaultLimit: Int = 20,
    maxLimit: Int = 50,
): Pagination {
    val limitRaw = request.queryParameters["limit"]
    val offsetRaw = request.queryParameters["offset"]

    val limit = if (limitRaw == null) {
        defaultLimit
    } else {
        limitRaw.toIntOrNull()
            ?.takeIf { it in 1..maxLimit }
            ?: throw DomainException(
                DomainError.ValidationError("limit must be between 1 and $maxLimit")
            )
    }

    val offset = if (offsetRaw == null) {
        0
    } else {
        offsetRaw.toIntOrNull()
            ?.takeIf { it >= 0 }
            ?: throw DomainException(
                DomainError.ValidationError("offset must be >= 0")
            )
    }

    return Pagination(limit = limit, offset = offset)
}
