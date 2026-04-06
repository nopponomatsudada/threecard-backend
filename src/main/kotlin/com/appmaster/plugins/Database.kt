package com.appmaster.plugins

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.application.*
import org.jetbrains.exposed.v1.jdbc.Database

fun Application.configureDatabase() {
    val dbUrl = configValue("database.url", "DATABASE_URL", "jdbc:postgresql://localhost:5432/appmaster")
    val dbUser = configValue("database.user", "DATABASE_USER", "appmaster")
    val dbPassword = configValue("database.password", "DATABASE_PASSWORD", "appmaster_dev_password")

    val config = HikariConfig().apply {
        jdbcUrl = dbUrl
        driverClassName = "org.postgresql.Driver"
        username = dbUser
        password = dbPassword
        maximumPoolSize = 10
        minimumIdle = 2
        idleTimeout = 30000
        connectionTimeout = 30000
        maxLifetime = 600000
        isAutoCommit = false
        transactionIsolation = "TRANSACTION_REPEATABLE_READ"
        validate()
    }

    try {
        Database.connect(HikariDataSource(config))
        log.info("Database connection established successfully")
    } catch (e: Exception) {
        log.warn("Database connection failed: ${e.message}. App will continue without database.")
    }
}

private fun Application.configValue(configKey: String, envVar: String, default: String): String =
    environment.config.propertyOrNull(configKey)?.getString()
        ?: System.getenv(envVar)
        ?: default
