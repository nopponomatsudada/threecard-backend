@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.appmaster.plugins

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import java.util.UUID
import javax.sql.DataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Verifies the V1 baseline + V2 device-secret migrations apply cleanly against
 * a fresh H2 database. This is the same flow `Database.kt` runs in production
 * (minus Postgres-specific quirks).
 */
class FlywayMigrationTest {

    @Test
    fun `migrations apply cleanly on a fresh database`() {
        val dataSource = h2DataSource()
        try {
            Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .baselineOnMigrate(false)
                .load()
                .migrate()

            // Verify the schema we just built has the new columns/tables.
            dataSource.connection.use { conn ->
                conn.metaData.getColumns(null, null, "USERS", "DEVICE_SECRET_HASH").use { rs ->
                    assertTrue(rs.next(), "users.device_secret_hash should exist after V2")
                }
                conn.metaData.getTables(null, null, "REFRESH_TOKENS", null).use { rs ->
                    assertTrue(rs.next(), "refresh_tokens table should exist after V2")
                }
                conn.metaData.getTables(null, null, "JWT_BLOCKLIST", null).use { rs ->
                    assertTrue(rs.next(), "jwt_blocklist table should exist after V2")
                }
                // V10 drops admin_users — Cloudflare Access is now the SSOT.
                conn.metaData.getTables(null, null, "ADMIN_USERS", null).use { rs ->
                    assertTrue(!rs.next(), "admin_users table should be dropped after V10")
                }
            }

            // Re-running migrate should be a no-op.
            val rerun = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate()
            assertEquals(0, rerun.migrationsExecuted)
        } finally {
            (dataSource as HikariDataSource).close()
        }
    }

    private fun h2DataSource(): DataSource {
        val name = "flyway-test-${UUID.randomUUID()}"
        return HikariDataSource(
            HikariConfig().apply {
                jdbcUrl = "jdbc:h2:mem:$name;DB_CLOSE_DELAY=-1;MODE=PostgreSQL"
                driverClassName = "org.h2.Driver"
                username = "sa"
                password = ""
                maximumPoolSize = 2
            }
        )
    }
}
