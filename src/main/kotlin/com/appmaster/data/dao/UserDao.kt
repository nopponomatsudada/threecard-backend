@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.appmaster.data.dao

import com.appmaster.data.dbQuery
import com.appmaster.data.entity.UsersTable
import com.appmaster.domain.error.DomainError
import com.appmaster.domain.error.DomainException
import com.appmaster.domain.model.entity.User
import com.appmaster.domain.model.valueobject.DisplayId
import com.appmaster.domain.model.valueobject.UserId
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.exceptions.ExposedSQLException
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import java.sql.SQLException
import kotlin.time.Instant

class UserDao {

    suspend fun findById(id: UserId): User? = dbQuery {
        UsersTable.selectAll()
            .where { UsersTable.id eq id.value }
            .singleOrNull()
            ?.toUser()
    }

    suspend fun findByDeviceId(deviceId: String): User? = dbQuery {
        UsersTable.selectAll()
            .where { UsersTable.deviceId eq deviceId }
            .singleOrNull()
            ?.toUser()
    }

    suspend fun insert(user: User): User {
        val maxRetries = 3
        var currentUser = user
        repeat(maxRetries) { attempt ->
            try {
                return dbQuery {
                    UsersTable.insert {
                        it[id] = currentUser.id.value
                        it[this.deviceId] = currentUser.deviceId
                        it[deviceSecretHash] = currentUser.deviceSecretHash
                        it[displayId] = currentUser.displayId.value
                        it[plan] = currentUser.plan
                        it[createdAt] = currentUser.createdAt
                        it[updatedAt] = currentUser.updatedAt
                    }
                    currentUser
                }
            } catch (e: ExposedSQLException) {
                // Retry only on display_id uniqueness violation. Detect via SQLState
                // "23505" (unique_violation) — works for both PostgreSQL and H2.
                if (attempt < maxRetries - 1 && isDisplayIdUniqueViolation(e)) {
                    currentUser = currentUser.copy(displayId = DisplayId.generate())
                } else {
                    throw e
                }
            }
        }
        throw DomainException(DomainError.ServerError)
    }

    /** Used by the silent re-bootstrap path: an existing pre-rollout user gets
     *  a freshly issued device_secret_hash on next /auth/device. */
    suspend fun updateDeviceSecretHash(id: UserId, hash: String, updatedAt: Instant) = dbQuery {
        UsersTable.update({ UsersTable.id eq id.value }) {
            it[deviceSecretHash] = hash
            it[UsersTable.updatedAt] = updatedAt
        }
        Unit
    }

    private fun isDisplayIdUniqueViolation(e: ExposedSQLException): Boolean {
        val sqlEx = (e.cause as? SQLException) ?: e
        if (sqlEx.sqlState != "23505") return false
        // Both Postgres and H2 include the constraint/column name in the message;
        // match on column name to ensure we're not retrying a device_id collision.
        val msg = (sqlEx.message ?: "").lowercase()
        return msg.contains("display_id")
    }

    private fun ResultRow.toUser(): User = User(
        id = UserId(this[UsersTable.id]),
        deviceId = this[UsersTable.deviceId],
        deviceSecretHash = this[UsersTable.deviceSecretHash],
        displayId = DisplayId(this[UsersTable.displayId]),
        plan = this[UsersTable.plan],
        createdAt = this[UsersTable.createdAt],
        updatedAt = this[UsersTable.updatedAt]
    )
}
