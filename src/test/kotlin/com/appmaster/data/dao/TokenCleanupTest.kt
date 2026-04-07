@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.appmaster.data.dao

import com.appmaster.data.entity.RefreshTokensTable
import com.appmaster.data.entity.UsersTable
import com.appmaster.domain.model.`enum`.Plan
import com.appmaster.domain.model.valueobject.UserId
import com.appmaster.domain.repository.RefreshTokenRepository
import com.appmaster.routes.setupTestDatabase
import com.appmaster.routes.tearDownTestDatabase
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

class TokenCleanupTest {

    @BeforeTest fun setup() = setupTestDatabase()
    @AfterTest fun teardown() = tearDownTestDatabase()

    @Test
    fun `RefreshTokenDao deleteExpired removes only past-expiry rows`() = runBlocking {
        val dao = RefreshTokenDao()
        val now = Clock.System.now()
        val userId = UserId("test-user")

        // refresh_tokens.user_id is a FK to users.id
        transaction {
            UsersTable.insert {
                it[id] = userId.value
                it[deviceId] = "test-device"
                it[deviceSecretHash] = null
                it[displayId] = "u_clean"
                it[plan] = Plan.FREE
                it[createdAt] = now
                it[updatedAt] = now
            }
        }

        // Two expired, one fresh.
        dao.save(record("expired-1", userId, expiresAt = now - 1.days))
        dao.save(record("expired-2", userId, expiresAt = now - 5.days))
        dao.save(record("fresh-1",   userId, expiresAt = now + 1.days))

        dao.deleteExpired(now)

        val remaining = transaction { RefreshTokensTable.selectAll().count() }
        assertEquals(1L, remaining)
    }

    @Test
    fun `JwtBlocklistDao deleteExpired removes only past-expiry rows`() = runBlocking {
        val dao = JwtBlocklistDao()
        val now = Clock.System.now()

        dao.block("expired-jti", expiresAt = now - 1.days)
        dao.block("fresh-jti",   expiresAt = now + 1.days)

        dao.deleteExpired(now)

        assertEquals(false, dao.isBlocked("expired-jti"))
        assertEquals(true,  dao.isBlocked("fresh-jti"))
    }

    private fun record(
        id: String,
        userId: UserId,
        expiresAt: kotlin.time.Instant
    ) = RefreshTokenRepository.Record(
        id = id,
        userId = userId,
        tokenHash = "hash-$id",
        jti = id,
        expiresAt = expiresAt,
        revokedAt = null,
        createdAt = Clock.System.now()
    )
}
