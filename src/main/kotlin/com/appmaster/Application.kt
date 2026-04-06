package com.appmaster

import com.appmaster.plugins.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*

fun main() {
    embeddedServer(
        Netty,
        port = System.getenv("PORT")?.toIntOrNull() ?: 8080,
        host = "0.0.0.0",
        module = Application::module
    ).start(wait = true)
}

fun Application.module() {
    configureSerialization()
    configureSecurity()
    configureAuthentication()
    configureCors()
    configureRateLimit()
    configureCallLogging()
    configureStatusPages()
    configureDatabase()
    configureDI()
    configureRouting()
}
