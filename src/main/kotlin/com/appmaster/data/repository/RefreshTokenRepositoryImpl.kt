@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.appmaster.data.repository

import com.appmaster.data.dao.RefreshTokenDao
import com.appmaster.domain.model.valueobject.UserId
import com.appmaster.domain.repository.RefreshTokenRepository
import kotlin.time.Instant

class RefreshTokenRepositoryImpl(
    private val dao: RefreshTokenDao
) : RefreshTokenRepository {

    override suspend fun save(record: RefreshTokenRepository.Record) = dao.save(record)

    override suspend fun findByHash(tokenHash: String): RefreshTokenRepository.Record? =
        dao.findByHash(tokenHash)

    override suspend fun revoke(id: String, revokedAt: Instant) = dao.revoke(id, revokedAt)

    override suspend fun revokeAllForUser(userId: UserId, revokedAt: Instant) =
        dao.revokeAllForUser(userId, revokedAt)

    override suspend fun deleteExpired(now: Instant) = dao.deleteExpired(now)
}
