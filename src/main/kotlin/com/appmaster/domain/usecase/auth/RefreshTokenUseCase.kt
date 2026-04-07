package com.appmaster.domain.usecase.auth

import com.appmaster.domain.error.DomainError
import com.appmaster.domain.error.DomainException
import com.appmaster.domain.repository.RefreshTokenRepository
import com.appmaster.domain.repository.UserRepository
import com.appmaster.domain.service.TokenProvider
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Rotates a refresh token. On success, the presented token is revoked and a
 * fresh access+refresh pair is issued.
 *
 * Reuse defense: presenting a token whose record is already revoked is treated
 * as evidence of theft. We revoke ALL of that user's outstanding refresh tokens
 * before throwing.
 */
@OptIn(ExperimentalTime::class)
class RefreshTokenUseCase(
    private val refreshTokenRepository: RefreshTokenRepository,
    private val userRepository: UserRepository,
    private val tokenProvider: TokenProvider,
    private val clock: Clock = Clock.System
) {
    data class Result(
        val accessToken: TokenProvider.AccessToken,
        val refreshToken: TokenProvider.RefreshToken
    )

    suspend operator fun invoke(plainRefreshToken: String): Result {
        if (plainRefreshToken.isBlank()) {
            throw DomainException(DomainError.InvalidRefreshToken)
        }

        val hash = tokenProvider.hashRefreshToken(plainRefreshToken)
        val record = refreshTokenRepository.findByHash(hash)
            ?: throw DomainException(DomainError.InvalidRefreshToken)

        val now = clock.now()

        if (record.revokedAt != null) {
            // Reuse detection: nuke every refresh token for this user.
            refreshTokenRepository.revokeAllForUser(record.userId, now)
            throw DomainException(DomainError.InvalidRefreshToken)
        }

        if (record.expiresAt <= now) {
            throw DomainException(DomainError.InvalidRefreshToken)
        }

        val user = userRepository.findById(record.userId)
            ?: throw DomainException(DomainError.InvalidRefreshToken)

        // Revoke the presented token, issue a new pair, persist the new record.
        refreshTokenRepository.revoke(record.id, now)

        val newAccess = tokenProvider.generateAccessToken(user)
        val newRefresh = tokenProvider.generateRefreshToken()
        refreshTokenRepository.save(
            RefreshTokenRepository.Record(
                id = newRefresh.jti,
                userId = user.id,
                tokenHash = newRefresh.tokenHash,
                jti = newRefresh.jti,
                expiresAt = newRefresh.expiresAt,
                revokedAt = null,
                createdAt = now
            )
        )

        return Result(newAccess, newRefresh)
    }
}
