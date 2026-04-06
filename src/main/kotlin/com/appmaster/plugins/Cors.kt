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

        if (isDevelopment && allowedHosts.isEmpty()) {
            // Development mode: allow any host for local testing
            anyHost()
            appEnvironment.log.warn("CORS: anyHost() enabled - DO NOT use in production!")
        } else if (allowedHosts.isNotEmpty()) {
            // Production mode: only allow specified hosts
            allowedHosts.forEach { host ->
                val trimmed = host.trim()
                if (trimmed.startsWith("https://")) {
                    allowHost(trimmed.removePrefix("https://"), schemes = listOf("https"))
                } else if (trimmed.startsWith("http://")) {
                    allowHost(trimmed.removePrefix("http://"), schemes = listOf("http"))
                } else {
                    allowHost(trimmed, schemes = listOf("https"))
                }
            }
            appEnvironment.log.info("CORS: Allowed hosts: $allowedHosts")
        } else {
            // Production without configured hosts - fail safe
            appEnvironment.log.error("CORS: No allowed hosts configured in production mode!")
            throw IllegalStateException("CORS_ALLOWED_HOSTS must be configured in production")
        }
    }
}
