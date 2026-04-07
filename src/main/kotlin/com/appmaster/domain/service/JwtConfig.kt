package com.appmaster.domain.service

import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

/**
 * Single source of truth for JWT/refresh-token configuration.
 * Loaded once at boot from environment, then injected into both
 * the Authentication plugin and `JwtTokenProvider`.
 *
 * The dev-secret guard lives in the loader (see `AppModule`), so the
 * value here is always production-safe by the time it reaches consumers.
 */
data class JwtConfig(
    val secret: String,
    val issuer: String,
    val audience: String,
    val realm: String,
    val accessTokenTtl: Duration = DEFAULT_ACCESS_TTL,
    val refreshTokenTtl: Duration = DEFAULT_REFRESH_TTL
) {
    companion object {
        val DEFAULT_ACCESS_TTL: Duration = 15.minutes
        val DEFAULT_REFRESH_TTL: Duration = 7.days
        const val DEV_SECRET: String = "dev-secret-change-in-production"
    }
}
