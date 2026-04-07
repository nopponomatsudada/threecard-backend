package com.appmaster

import com.appmaster.plugins.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.callid.*
import io.ktor.server.plugins.forwardedheaders.*
import java.util.UUID

fun main() {
    embeddedServer(
        Netty,
        port = System.getenv("PORT")?.toIntOrNull() ?: 8080,
        host = "0.0.0.0",
        module = Application::module
    ).start(wait = true)
}

fun Application.module() {
    // X-Forwarded-* must be processed before any plugin reads remoteHost / scheme.
    install(XForwardedHeaders)

    // Per-request correlation id, surfaced via MDC for log correlation.
    install(CallId) {
        retrieveFromHeader("X-Request-Id")
        generate { UUID.randomUUID().toString() }
        verify { it.isNotEmpty() }
    }

    configureSerialization()
    configureSecurity()
    // DI must come before Authentication: validate{} consults JwtBlocklistRepository
    // via Koin, so the container needs to exist first.
    configureDatabase()
    configureDI()
    configureAuthentication()
    configureCors()
    configureRateLimit()
    configureCallLogging()
    configureStatusPages()
    configureRouting()
    configureTokenCleanup()
}
