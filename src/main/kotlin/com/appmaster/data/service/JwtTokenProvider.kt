@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.appmaster.data.service

import com.appmaster.domain.model.entity.User
import com.appmaster.domain.service.JwtConfig
import com.appmaster.domain.service.TokenProvider
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.Date
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.Instant

class JwtTokenProvider(
    private val config: JwtConfig,
    private val clock: Clock = Clock.System
) : TokenProvider {

    private val algorithm = Algorithm.HMAC256(config.secret)
    private val verifier = JWT.require(algorithm)
        .withIssuer(config.issuer)
        .withAudience(config.audience)
        .build()
    private val secureRandom = SecureRandom()
    private val base64Url = Base64.getUrlEncoder().withoutPadding()

    override fun generateAccessToken(user: User): TokenProvider.AccessToken {
        val now = clock.now()
        val expiresAt = now + config.accessTokenTtl
        val jti = UUID.randomUUID().toString()
        val token = JWT.create()
            .withAudience(config.audience)
            .withIssuer(config.issuer)
            .withSubject(user.id.value)
            .withClaim(TokenProvider.CLAIM_USER_ID, user.id.value)
            .withJWTId(jti)
            .withIssuedAt(Date(now.toEpochMilliseconds()))
            .withExpiresAt(Date(expiresAt.toEpochMilliseconds()))
            .sign(algorithm)
        return TokenProvider.AccessToken(token = token, jti = jti, expiresAt = expiresAt)
    }

    override fun generateRefreshToken(): TokenProvider.RefreshToken {
        val bytes = ByteArray(REFRESH_TOKEN_BYTES)
        secureRandom.nextBytes(bytes)
        val plain = base64Url.encodeToString(bytes)
        val now = clock.now()
        return TokenProvider.RefreshToken(
            plain = plain,
            tokenHash = hashRefreshToken(plain),
            jti = UUID.randomUUID().toString(),
            expiresAt = now + config.refreshTokenTtl
        )
    }

    override fun hashRefreshToken(plain: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(plain.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    override fun parseClaims(token: String): TokenProvider.ParsedClaims? = runCatching {
        val decoded = verifier.verify(token)
        TokenProvider.ParsedClaims(
            userId = decoded.getClaim(TokenProvider.CLAIM_USER_ID).asString(),
            jti = decoded.id ?: return null,
            expiresAt = Instant.fromEpochMilliseconds(decoded.expiresAt.time)
        )
    }.getOrNull()

    companion object {
        private const val REFRESH_TOKEN_BYTES = 32
    }
}
