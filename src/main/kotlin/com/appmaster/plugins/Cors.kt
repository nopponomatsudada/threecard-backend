package com.appmaster.plugins

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.cors.routing.*

fun Application.configureCors() {
    val allowedHosts = environment.config.propertyOrNull("cors.allowedHosts")?.getList()
        ?: System.getenv("CORS_ALLOWED_HOSTS")?.split(",")
        ?: emptyList()

    val isDevelopment = developmentMode
    val appEnvironment = environment

    install(CORS) {
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Patch)
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Accept)

        if (allowedHosts.isNotEmpty()) {
            allowedHosts.forEach { host ->
                val trimmed = host.trim()
                when {
                    trimmed.startsWith("https://") ->
                        allowHost(trimmed.removePrefix("https://"), schemes = listOf("https"))
                    trimmed.startsWith("http://") ->
                        allowHost(trimmed.removePrefix("http://"), schemes = listOf("http"))
                    else ->
                        allowHost(trimmed, schemes = listOf("https"))
                }
            }
            appEnvironment.log.info("CORS: allowed hosts: $allowedHosts")
        } else if (isDevelopment) {
            // Dev fallback — explicit local origins only. NEVER anyHost(),
            // because we accept Authorization headers.
            allowHost("localhost:8080")
            allowHost("localhost:3000")
            allowHost("localhost:5173")
            allowHost("127.0.0.1:8080")
            allowHost("10.0.2.2:8080") // Android emulator
            appEnvironment.log.warn("CORS: dev mode — restricted to localhost / 10.0.2.2")
        } else {
            appEnvironment.log.error("CORS: no allowed hosts configured in production mode")
            throw IllegalStateException("CORS_ALLOWED_HOSTS must be configured in production")
        }
    }
}
