package com.appmaster.data.service

import com.appmaster.domain.model.entity.User
import com.appmaster.domain.service.TokenProvider
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import java.util.Date

class JwtTokenProvider(
    private val secret: String,
    private val issuer: String,
    private val audience: String,
    private val accessTokenExpirationMs: Long
) : TokenProvider {

    private val algorithm = Algorithm.HMAC256(secret)

    override fun generateAccessToken(user: User): String {
        val now = System.currentTimeMillis()
        return JWT.create()
            .withAudience(audience)
            .withIssuer(issuer)
            .withSubject(user.id.value)
            .withClaim(TokenProvider.CLAIM_USER_ID, user.id.value)
            .withIssuedAt(Date(now))
            .withExpiresAt(Date(now + accessTokenExpirationMs))
            .sign(algorithm)
    }

}
