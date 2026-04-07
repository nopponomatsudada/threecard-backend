@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.appmaster.data.dao

import com.appmaster.data.dbQuery
import com.appmaster.data.entity.RefreshTokensTable
import com.appmaster.domain.model.valueobject.UserId
import com.appmaster.domain.repository.RefreshTokenRepository
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.time.Instant

class RefreshTokenDao {

    suspend fun save(record: RefreshTokenRepository.Record) = dbQuery {
        RefreshTokensTable.insert {
            it[id] = record.id
            it[userId] = record.userId.value
            it[tokenHash] = record.tokenHash
            it[jti] = record.jti
            it[expiresAt] = record.expiresAt
            it[revokedAt] = record.revokedAt
            it[createdAt] = record.createdAt
        }
        Unit
    }

    suspend fun findByHash(tokenHash: String): RefreshTokenRepository.Record? = dbQuery {
        RefreshTokensTable.selectAll()
            .where { RefreshTokensTable.tokenHash eq tokenHash }
            .singleOrNull()
            ?.toRecord()
    }

    suspend fun revoke(id: String, revokedAt: Instant) = dbQuery {
        RefreshTokensTable.update({ RefreshTokensTable.id eq id }) {
            it[RefreshTokensTable.revokedAt] = revokedAt
        }
        Unit
    }

    suspend fun revokeAllForUser(userId: UserId, revokedAt: Instant) = dbQuery {
        RefreshTokensTable.update({
            (RefreshTokensTable.userId eq userId.value) and
                RefreshTokensTable.revokedAt.isNull()
        }) {
            it[RefreshTokensTable.revokedAt] = revokedAt
        }
        Unit
    }

    suspend fun deleteExpired(now: Instant) = dbQuery {
        RefreshTokensTable.deleteWhere { expiresAt lessEq now }
        Unit
    }

    private fun ResultRow.toRecord(): RefreshTokenRepository.Record =
        RefreshTokenRepository.Record(
            id = this[RefreshTokensTable.id],
            userId = UserId(this[RefreshTokensTable.userId]),
            tokenHash = this[RefreshTokensTable.tokenHash],
            jti = this[RefreshTokensTable.jti],
            expiresAt = this[RefreshTokensTable.expiresAt],
            revokedAt = this[RefreshTokensTable.revokedAt],
            createdAt = this[RefreshTokensTable.createdAt]
        )
}
