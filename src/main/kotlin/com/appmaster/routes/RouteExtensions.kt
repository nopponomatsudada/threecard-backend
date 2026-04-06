package com.appmaster.routes

import com.appmaster.domain.error.DomainError
import com.appmaster.domain.error.DomainException
import com.appmaster.domain.model.valueobject.UserId
import com.appmaster.domain.service.TokenProvider
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.routing.*

fun RoutingCall.requireUserId(): UserId {
    val principal = principal<JWTPrincipal>()
    val id = principal?.payload?.getClaim(TokenProvider.CLAIM_USER_ID)?.asString()
        ?: throw DomainException(DomainError.Unauthorized)
    return UserId(id)
}
