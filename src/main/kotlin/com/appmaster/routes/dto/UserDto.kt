package com.appmaster.routes.dto

import kotlinx.serialization.Serializable

@Serializable
data class UserProfileResponse(
    val id: String,
    val displayId: String,
    val bestCount: Int,
    val collectionCount: Int,
    val plan: String,
    val createdAt: String
)
