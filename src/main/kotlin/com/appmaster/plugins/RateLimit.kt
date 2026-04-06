package com.appmaster.plugins

import io.ktor.server.application.*
import io.ktor.server.plugins.ratelimit.*
import kotlin.time.Duration.Companion.minutes

/**
 * Configure rate limiting for the application.
 *
 * Rate limits protect against:
 * - Brute force attacks on authentication endpoints
 * - API abuse and DoS attacks
 * - Resource exhaustion
 */
fun Application.configureRateLimit() {
    install(RateLimit) {
        // Strict rate limit for authentication endpoints
        // 5 attempts per minute per IP to prevent brute force
        register(RateLimitName("auth")) {
            rateLimiter(limit = 5, refillPeriod = 1.minutes)
            requestKey { call ->
                call.request.local.remoteAddress
            }
        }

        // Rate limit for OTP verification
        // 3 attempts per minute per IP
        register(RateLimitName("otp")) {
            rateLimiter(limit = 3, refillPeriod = 1.minutes)
            requestKey { call ->
                call.request.local.remoteAddress
            }
        }

        // General API rate limit
        // 100 requests per minute per IP
        register(RateLimitName("api")) {
            rateLimiter(limit = 100, refillPeriod = 1.minutes)
            requestKey { call ->
                call.request.local.remoteAddress
            }
        }

        // Sensitive operations rate limit
        // 10 requests per minute (transfers, password changes)
        register(RateLimitName("sensitive")) {
            rateLimiter(limit = 10, refillPeriod = 1.minutes)
            requestKey { call ->
                call.request.local.remoteAddress
            }
        }
    }
}
