@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.appmaster.data.dao

import com.appmaster.data.dbQuery
import com.appmaster.data.entity.UsersTable
import com.appmaster.domain.model.`enum`.Plan
import com.appmaster.domain.model.entity.User
import com.appmaster.domain.model.valueobject.DisplayId
import com.appmaster.domain.model.valueobject.UserId
import com.appmaster.domain.error.DomainError
import com.appmaster.domain.error.DomainException
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.exceptions.ExposedSQLException

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
                        it[displayId] = currentUser.displayId.value
                        it[plan] = currentUser.plan
                        it[createdAt] = currentUser.createdAt
                        it[updatedAt] = currentUser.updatedAt
                    }
                    currentUser
                }
            } catch (e: ExposedSQLException) {
                // Retry with new displayId on unique constraint violation
                if (attempt < maxRetries - 1 && e.message?.contains("display_id") == true) {
                    currentUser = currentUser.copy(displayId = DisplayId.generate())
                } else {
                    throw e
                }
            }
        }
        throw DomainException(DomainError.ServerError)
    }

    private fun ResultRow.toUser(): User = User(
        id = UserId(this[UsersTable.id]),
        deviceId = this[UsersTable.deviceId],
        displayId = DisplayId(this[UsersTable.displayId]),
        plan = this[UsersTable.plan],
        createdAt = this[UsersTable.createdAt],
        updatedAt = this[UsersTable.updatedAt]
    )
}
