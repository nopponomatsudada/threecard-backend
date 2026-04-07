@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.appmaster.data.dao

import com.appmaster.data.dbQuery
import com.appmaster.data.entity.JwtBlocklistTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.exceptions.ExposedSQLException
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import kotlin.time.Instant

class JwtBlocklistDao {

    suspend fun block(jti: String, expiresAt: Instant) = dbQuery {
        try {
            JwtBlocklistTable.insert {
                it[JwtBlocklistTable.jti] = jti
                it[JwtBlocklistTable.expiresAt] = expiresAt
            }
        } catch (e: ExposedSQLException) {
            // Already blocklisted — no-op. Avoid `insertIgnore` because plain H2
            // (no MySQL mode) does not support `INSERT IGNORE`.
        }
        Unit
    }

    suspend fun isBlocked(jti: String): Boolean = dbQuery {
        JwtBlocklistTable.selectAll()
            .where { JwtBlocklistTable.jti eq jti }
            .empty()
            .not()
    }

    suspend fun deleteExpired(now: Instant) = dbQuery {
        JwtBlocklistTable.deleteWhere { expiresAt lessEq now }
        Unit
    }
}
