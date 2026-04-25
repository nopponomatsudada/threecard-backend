package com.appmaster.plugins

import com.appmaster.routes.*
import io.ktor.server.application.*
import io.ktor.server.routing.*

fun Application.configureRouting() {
    routing {
        healthRoutes()
        authRoutes()
        adminMeRoutes()
        userRoutes()
        tagRoutes()
        themeRoutes()
        bestRoutes()
        discoverRoutes()
        bookmarkRoutes()
        moderationRoutes()
    }
}
