@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.appmaster.data.repository

import com.appmaster.data.dao.JwtBlocklistDao
import com.appmaster.domain.repository.JwtBlocklistRepository
import kotlin.time.Instant

class JwtBlocklistRepositoryImpl(
    private val dao: JwtBlocklistDao
) : JwtBlocklistRepository {

    override suspend fun block(jti: String, expiresAt: Instant) = dao.block(jti, expiresAt)

    override suspend fun isBlocked(jti: String): Boolean = dao.isBlocked(jti)

    override suspend fun deleteExpired(now: Instant) = dao.deleteExpired(now)
}
