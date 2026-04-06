package com.appmaster.plugins

import com.appmaster.domain.error.DomainError
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*

fun Application.configureAuthentication() {
    val secret = configValue("jwt.secret", "JWT_SECRET", "dev-secret-change-in-production")
    val issuer = configValue("jwt.issuer", "JWT_ISSUER", "appmaster")
    val audience = configValue("jwt.audience", "JWT_AUDIENCE", "appmaster-app")
    val realm = configValue("jwt.realm", "JWT_REALM", "AppMaster")

    install(Authentication) {
        jwt("jwt") {
            this.realm = realm

            verifier(
                JWT.require(Algorithm.HMAC256(secret))
                    .withIssuer(issuer)
                    .withAudience(audience)
                    .build()
            )

            validate { credential ->
                if (credential.payload.audience.contains(audience)) {
                    JWTPrincipal(credential.payload)
                } else {
                    null
                }
            }

            challenge { _, _ ->
                call.respond(
                    HttpStatusCode.Unauthorized,
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
}
