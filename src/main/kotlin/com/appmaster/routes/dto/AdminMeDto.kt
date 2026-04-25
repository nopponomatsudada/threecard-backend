package com.appmaster.routes.dto

import kotlinx.serialization.Serializable

@Serializable
data class AdminProfileDto(
    val id: String,
    val email: String,
    val displayName: String
)
