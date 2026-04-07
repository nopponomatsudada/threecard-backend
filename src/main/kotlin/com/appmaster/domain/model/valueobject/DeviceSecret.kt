package com.appmaster.domain.model.valueobject

import java.security.SecureRandom
import java.util.Base64

/**
 * High-entropy server-issued secret bound to a deviceId.
 * Generated once on bootstrap (`POST /auth/device` first call) and persisted
 * by the client; required for all subsequent device-auth calls.
 *
 * Stored on the server as BCrypt(plain).
 */
@JvmInline
value class DeviceSecret(val value: String) {
    companion object {
        private const val SECRET_BYTES = 32
        private val secureRandom = SecureRandom()
        private val base64 = Base64.getUrlEncoder().withoutPadding()

        fun generate(): DeviceSecret {
            val bytes = ByteArray(SECRET_BYTES)
            secureRandom.nextBytes(bytes)
            return DeviceSecret(base64.encodeToString(bytes))
        }
    }
}
