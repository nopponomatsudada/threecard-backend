package com.appmaster.domain.usecase.auth

import com.appmaster.domain.model.valueobject.UserId
import com.appmaster.domain.repository.JwtBlocklistRepository
import com.appmaster.domain.repository.RefreshTokenRepository
import com.appmaster.domain.service.TokenProvider
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Revokes the user's current session: blocklists the access JWT until its
 * original `exp`, and revokes all of the user's outstanding refresh tokens.
 */
@OptIn(ExperimentalTime::class)
class LogoutUseCase(
    private val refreshTokenRepository: RefreshTokenRepository,
    private val jwtBlocklistRepository: JwtBlocklistRepository,
    private val clock: Clock = Clock.System
) {
    suspend operator fun invoke(
        userId: UserId,
        accessTokenClaims: TokenProvider.ParsedClaims
    ) {
        val now = clock.now()
        jwtBlocklistRepository.block(accessTokenClaims.jti, accessTokenClaims.expiresAt)
        refreshTokenRepository.revokeAllForUser(userId, now)
    }
}
