package com.appmaster.domain.repository

import com.appmaster.domain.model.valueobject.UserId
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Persistence for opaque refresh tokens. The plain token never leaves the
 * issuing call site; only its sha256 hash is stored, looked up, and matched.
 */
@OptIn(ExperimentalTime::class)
interface RefreshTokenRepository {
    data class Record(
        val id: String,
        val userId: UserId,
        val tokenHash: String,
        val jti: String,
        val expiresAt: Instant,
        val revokedAt: Instant?,
        val createdAt: Instant
    )

    suspend fun save(record: Record)
    suspend fun findByHash(tokenHash: String): Record?
    suspend fun revoke(id: String, revokedAt: Instant)
    suspend fun revokeAllForUser(userId: UserId, revokedAt: Instant)
    suspend fun deleteExpired(now: Instant)
}
