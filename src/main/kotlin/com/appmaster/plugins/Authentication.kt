package com.appmaster.plugins

import com.appmaster.domain.repository.JwtBlocklistRepository
import com.appmaster.domain.service.JwtConfig
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import org.koin.ktor.ext.get

fun Application.configureAuthentication() {
    val cfConfig = loadCloudflareAccessConfig()
    val devMode = environment.config.propertyOrNull("ktor.development")?.getString()?.toBoolean() == true
    if (!cfConfig.isEnabled) {
        if (!devMode) {
            throw IllegalStateException(
                "CF_ACCESS_TEAM_DOMAIN and CF_ACCESS_AUD_TAG must be configured in production"
            )
        }
        log.warn("CF Access disabled: CF_ACCESS_TEAM_DOMAIN / CF_ACCESS_AUD_TAG missing — cf-access realm will reject all requests")
    }
    configureAuthentication(cloudflareJwksVerifier(cfConfig))
}

fun Application.configureAuthentication(cfAccessVerifier: CfAccessTokenVerifier) {
    val config: JwtConfig = get()
    val blocklist: JwtBlocklistRepository = get()

    install(Authentication) {
        installCfAccess(cfAccessVerifier)

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

            challenge { _, _ -> call.respondUnauthorized() }
        }
    }
}
