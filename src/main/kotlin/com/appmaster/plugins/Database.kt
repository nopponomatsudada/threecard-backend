package com.appmaster.plugins

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.application.*
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.v1.jdbc.Database
import javax.sql.DataSource

private const val DEV_DATABASE_PASSWORD = "appmaster_dev_password"

@OptIn(kotlin.time.ExperimentalTime::class)
fun Application.configureDatabase() {
    val dbUrl = configValue("database.url", "DATABASE_URL", "jdbc:postgresql://localhost:5432/appmaster")
    val dbUser = configValue("database.user", "DATABASE_USER", "appmaster")
    val dbPassword = configValue("database.password", "DATABASE_PASSWORD", DEV_DATABASE_PASSWORD)
    val driver = configValue("database.driver", "DATABASE_DRIVER", "org.postgresql.Driver")

    // Mirror the JWT_SECRET dev-default guard in AppModule: if we're not in
    // development mode and the operator forgot to set DATABASE_PASSWORD, fail
    // fast rather than booting with a known-weak credential.
    val devMode = environment.config.propertyOrNull("ktor.development")?.getString()?.toBoolean() == true
    if (!devMode && dbPassword == DEV_DATABASE_PASSWORD) {
        throw IllegalStateException(
            "DATABASE_PASSWORD must be set to a non-default value in production"
        )
    }

    val config = HikariConfig().apply {
        jdbcUrl = dbUrl
        driverClassName = driver
        username = dbUser
        password = dbPassword
        maximumPoolSize = 10
        minimumIdle = 2
        idleTimeout = 30000
        connectionTimeout = 30000
        maxLifetime = 600000
        isAutoCommit = false
        // H2 (used by tests) does not support REPEATABLE_READ; only set explicit
        // isolation for real DB drivers.
        if (!driver.contains("h2", ignoreCase = true)) {
            transactionIsolation = "TRANSACTION_REPEATABLE_READ"
        }
        validate()
    }

    try {
        val dataSource: DataSource = HikariDataSource(config)
        Database.connect(dataSource)
        log.info("Database connection established")

        runMigrations(dataSource, isH2 = driver.contains("h2", ignoreCase = true))
        log.info("Database migrations applied")
    } catch (e: Exception) {
        // Hard-fail: a half-broken process is worse than a crash. The orchestrator
        // (ECS / docker-compose / Kubernetes) will restart the container.
        log.error("Database initialization failed", e)
        throw IllegalStateException("Database initialization failed: ${e.message}", e)
    }
}

private fun Application.runMigrations(dataSource: DataSource, isH2: Boolean) {
    // baselineOnMigrate: existing prod databases that already have the BE-1..BE-7
    // schema (created by `createMissingTablesAndColumns`) will be tagged at
    // baselineVersion=1 on first migrate; the V2 migration then runs to add
    // device_secret_hash + refresh_tokens + jwt_blocklist.
    //
    // Tests use H2 with the production V1+V2 SQL — keep V1 SQL ANSI-compatible.
    val locations = if (isH2) {
        arrayOf("classpath:db/migration")
    } else {
        arrayOf("classpath:db/migration", "classpath:db/migration-postgresql")
    }
    val flyway = Flyway.configure()
        .dataSource(dataSource)
        .locations(*locations)
        .baselineOnMigrate(true)
        .baselineVersion("1")
        .baselineDescription("Pre-flyway baseline (BE-1..BE-7)")
        // H2 needs explicit dialect detection skip (Flyway auto-detects via JDBC).
        .load()
    flyway.migrate()
}
