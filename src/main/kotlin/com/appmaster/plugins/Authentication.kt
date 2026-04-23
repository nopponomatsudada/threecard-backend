package com.appmaster.plugins

import com.appmaster.domain.error.DomainError
import com.appmaster.domain.repository.JwtBlocklistRepository
import com.appmaster.domain.service.JwtConfig
import com.appmaster.domain.service.TokenProvider
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*
import org.koin.ktor.ext.get
import org.slf4j.LoggerFactory

private val authLog = LoggerFactory.getLogger("com.appmaster.auth")

fun Application.configureAuthentication() {
    // JwtConfig is loaded once in AppModule (single source of truth, includes
    // the dev-secret guard). Pull it from Koin.
    val config: JwtConfig = get()
    val blocklist: JwtBlocklistRepository = get()

    val adminApiKey = environment.config.propertyOrNull("ktor.admin.apiKey")?.getString()
        ?: System.getenv("ADMIN_API_KEY")

    if (adminApiKey.isNullOrBlank()) {
        authLog.warn("ADMIN_API_KEY is not set — admin endpoints will reject all requests")
    }

    install(Authentication) {
        bearer("admin") {
            authenticate { credential ->
                if (adminApiKey != null && credential.token == adminApiKey) {
                    UserIdPrincipal("admin")
                } else {
                    null
                }
            }
        }

        jwt("jwt") {
            this.realm = config.realm

            verifier(
                JWT.require(Algorithm.HMAC256(config.secret))
                    .withIssuer(config.issuer)
                    .withAudience(config.audience)
                    .build()
            )

            validate { credential ->
                if (!credential.payload.audience.contains(config.audience)) {
                    return@validate null
                }
                val jti = credential.payload.id
                if (jti != null && blocklist.isBlocked(jti)) {
                    return@validate null
                }
                JWTPrincipal(credential.payload)
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
