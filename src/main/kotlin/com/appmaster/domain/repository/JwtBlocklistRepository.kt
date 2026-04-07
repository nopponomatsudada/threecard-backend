package com.appmaster.domain.repository

import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Tracks revoked JWT `jti` claims until their original `exp`.
 * Authentication validate{} consults this on every request.
 */
@OptIn(ExperimentalTime::class)
interface JwtBlocklistRepository {
    suspend fun block(jti: String, expiresAt: Instant)
    suspend fun isBlocked(jti: String): Boolean
    suspend fun deleteExpired(now: Instant)
}
