package com.appmaster.plugins

import io.ktor.server.application.*

internal fun Application.configValue(configKey: String, envVar: String, default: String): String =
    environment.configValue(configKey, envVar, default)

internal fun ApplicationEnvironment.configValue(configKey: String, envVar: String, default: String): String =
    config.propertyOrNull(configKey)?.getString()
        ?: System.getenv(envVar)
        ?: default
