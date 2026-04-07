package com.appmaster.routes.dto

import kotlinx.serialization.Serializable

@Serializable
data class DeviceAuthRequest(
    val deviceId: String,
    /** Optional on the very first call (bootstrap). Required for every
     *  subsequent login on the same `deviceId`. */
    val deviceSecret: String? = null
)

@Serializable
data class AuthResponseData(
    val accessToken: String,
    val refreshToken: String,
    /** Access-token TTL in seconds. */
    val expiresIn: Long,
    /** Present **only** on the bootstrap response (HTTP 201). The client must
     *  persist this securely; the server will never re-issue it. */
    val deviceSecret: String? = null,
    val user: AuthUserDto
)

@Serializable
data class AuthUserDto(
    val id: String,
    val displayId: String
)

@Serializable
data class RefreshTokenRequest(
    val refreshToken: String
)

@Serializable
data class RefreshTokenResponse(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Long
)
