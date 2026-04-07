package com.appmaster.domain.service

import com.appmaster.domain.model.entity.User
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Issues short-lived JWT access tokens and opaque refresh tokens.
 * `jti` is a unique per-token id used for blocklist + refresh-rotation tracking.
 */
@OptIn(ExperimentalTime::class)
interface TokenProvider {
    /** Issued JWT access token plus the `jti` it carries and its expiry. */
    data class AccessToken(
        val token: String,
        val jti: String,
        val expiresAt: Instant
    )

    /** Issued opaque refresh token: plain value (returned to client once)
     * + sha256 hex hash (persisted server-side) + jti + expiry. */
    data class RefreshToken(
        val plain: String,
        val tokenHash: String,
        val jti: String,
        val expiresAt: Instant
    )

    /** Parsed claims from a JWT. Returned as raw strings for use in logout. */
    data class ParsedClaims(
        val userId: String,
        val jti: String,
        val expiresAt: Instant
    )

    fun generateAccessToken(user: User): AccessToken
    fun generateRefreshToken(): RefreshToken

    /** Hash a plain refresh token the same way the provider does on issue.
     * Used by the refresh use case to look up the persisted record. */
    fun hashRefreshToken(plain: String): String

    /** Parse claims from a JWT *without* verifying signature; intended only
     * for already-authenticated principals (logout). Returns null on parse failure. */
    fun parseClaims(token: String): ParsedClaims?

    companion object {
        const val CLAIM_USER_ID = "userId"
        const val CLAIM_JTI = "jti"
    }
}
