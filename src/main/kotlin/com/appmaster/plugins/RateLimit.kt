package com.appmaster.plugins

import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.ratelimit.*
import kotlin.time.Duration.Companion.minutes

/**
 * Rate-limit configuration. Keys are derived via `request.origin.remoteHost`,
 * which honours `X-Forwarded-For` (XForwardedHeaders is installed in Application.kt
 * before this plugin runs). Direct connections fall back to the TCP peer.
 */
fun Application.configureRateLimit() {
    install(RateLimit) {
        // Auth endpoints — strict to throttle brute force on /auth/device, /auth/refresh.
        register(RateLimitName("auth")) {
            rateLimiter(limit = 5, refillPeriod = 1.minutes)
            requestKey { call -> call.request.origin.remoteHost }
        }

        // OTP verification (reserved for Phase 2).
        register(RateLimitName("otp")) {
            rateLimiter(limit = 3, refillPeriod = 1.minutes)
            requestKey { call -> call.request.origin.remoteHost }
        }

        // General API — keyed per authenticated user, falling back to client IP.
        // Cloudflare-Access-authenticated admin routes carry an AdminPrincipal;
        // mobile / public routes carry a JWTPrincipal with the userId claim.
        register(RateLimitName("api")) {
            rateLimiter(limit = 100, refillPeriod = 1.minutes)
            requestKey { call ->
                call.principal<AdminPrincipal>()?.adminId
                    ?: call.principal<JWTPrincipal>()?.payload?.getClaim("userId")?.asString()
                    ?: call.request.origin.remoteHost
            }
        }

        // Sensitive operations.
        register(RateLimitName("sensitive")) {
            rateLimiter(limit = 10, refillPeriod = 1.minutes)
            requestKey { call -> call.request.origin.remoteHost }
        }
    }
}
