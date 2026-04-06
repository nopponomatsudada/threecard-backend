package com.appmaster.plugins

import com.appmaster.routes.*
import io.ktor.server.application.*
import io.ktor.server.routing.*

fun Application.configureRouting() {
    routing {
        // Health check
        healthRoutes()

        // Add your routes here as you implement them
        // Example:
        // authRoutes()
        // userRoutes()
    }
}
