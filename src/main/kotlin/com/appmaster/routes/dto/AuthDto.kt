package com.appmaster.routes.dto

import kotlinx.serialization.Serializable

@Serializable
data class DeviceAuthRequest(
    val deviceId: String
)

@Serializable
data class AuthResponseData(
    val accessToken: String,
    val user: AuthUserDto
)

@Serializable
data class AuthUserDto(
    val id: String,
    val displayId: String
)
