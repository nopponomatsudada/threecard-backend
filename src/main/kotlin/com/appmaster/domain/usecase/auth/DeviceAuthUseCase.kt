package com.appmaster.domain.usecase.auth

import com.appmaster.domain.error.DomainError
import com.appmaster.domain.error.DomainException
import com.appmaster.domain.model.entity.User
import com.appmaster.domain.model.valueobject.DeviceSecret
import com.appmaster.domain.repository.RefreshTokenRepository
import com.appmaster.domain.repository.UserRepository
import com.appmaster.domain.service.PasswordHasher
import com.appmaster.domain.service.TokenProvider
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Two-mode device authentication:
 *
 * - **Bootstrap** (deviceId not yet registered): generate a fresh `deviceSecret`,
 *   persist `bcrypt(deviceSecret)`, and return the plain secret to the client
 *   exactly once. Client must store it securely.
 * - **Login** (deviceId already registered): client must present the previously
 *   issued `deviceSecret`. We bcrypt-verify it. Server NEVER re-issues a secret
 *   for an existing device.
 *
 * Both paths issue a fresh access+refresh token pair on success.
 */
@OptIn(ExperimentalTime::class)
class DeviceAuthUseCase(
    private val userRepository: UserRepository,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val tokenProvider: TokenProvider,
    private val passwordHasher: PasswordHasher,
    private val clock: Clock = Clock.System
) {
    data class Result(
        val user: User,
        /** Plain device secret — present **only** on bootstrap. Persist client-side immediately. */
        val deviceSecret: String?,
        val accessToken: TokenProvider.AccessToken,
        val refreshToken: TokenProvider.RefreshToken,
        val isNewUser: Boolean
    )

    suspend operator fun invoke(deviceId: String, deviceSecret: String?): Result {
        if (deviceId.isBlank()) {
            throw DomainException(DomainError.ValidationError("deviceId は必須です"))
        }

        val existing = userRepository.findByDeviceId(deviceId)
        return if (existing == null) {
            bootstrap(deviceId)
        } else {
            login(existing, deviceSecret)
        }
    }

    private suspend fun bootstrap(deviceId: String): Result {
        val secret = DeviceSecret.generate()
        val secretHash = passwordHasher.hash(secret.value)
        val user = User.create(deviceId = deviceId, deviceSecretHash = secretHash, clock = clock)
        val saved = userRepository.save(user)
        val tokens = issueTokens(saved)
        return Result(
            user = saved,
            deviceSecret = secret.value,
            accessToken = tokens.first,
            refreshToken = tokens.second,
            isNewUser = true
        )
    }

    private suspend fun login(user: User, deviceSecret: String?): Result {
        // Silent re-bootstrap: a user that predates the device-secret rollout
        // has a null hash. The very next /auth/device call gets a freshly
        // issued secret, persisted server-side. We treat this as a 201 so the
        // client knows to store the returned secret.
        if (user.deviceSecretHash == null) {
            return rebootstrap(user)
        }
        if (deviceSecret.isNullOrBlank()) {
            throw DomainException(DomainError.InvalidDeviceCredentials)
        }
        if (!passwordHasher.verify(deviceSecret, user.deviceSecretHash)) {
            throw DomainException(DomainError.InvalidDeviceCredentials)
        }
        val tokens = issueTokens(user)
        return Result(
            user = user,
            deviceSecret = null,
            accessToken = tokens.first,
            refreshToken = tokens.second,
            isNewUser = false
        )
    }

    private suspend fun rebootstrap(user: User): Result {
        val secret = DeviceSecret.generate()
        val secretHash = passwordHasher.hash(secret.value)
        val updated = userRepository.updateDeviceSecretHash(user, secretHash)
        val tokens = issueTokens(updated)
        return Result(
            user = updated,
            deviceSecret = secret.value,
            accessToken = tokens.first,
            refreshToken = tokens.second,
            // Reported as bootstrap so the client persists the returned secret.
            isNewUser = true
        )
    }

    private suspend fun issueTokens(
        user: User
    ): Pair<TokenProvider.AccessToken, TokenProvider.RefreshToken> {
        val access = tokenProvider.generateAccessToken(user)
        val refresh = tokenProvider.generateRefreshToken()
        refreshTokenRepository.save(
            RefreshTokenRepository.Record(
                id = refresh.jti,
                userId = user.id,
                tokenHash = refresh.tokenHash,
                jti = refresh.jti,
                expiresAt = refresh.expiresAt,
                revokedAt = null,
                createdAt = clock.now()
            )
        )
        return access to refresh
    }
}
