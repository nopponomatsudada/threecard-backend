@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.appmaster.routes.dto

import com.appmaster.domain.model.entity.ProfileWithStats
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

fun ProfileWithStats.toDto() = UserProfileResponse(
    id = user.id.value,
    displayId = user.displayId.value,
    bestCount = bestCount,
    collectionCount = collectionCount,
    plan = user.plan.name.lowercase(),
    createdAt = user.createdAt.toString()
)
