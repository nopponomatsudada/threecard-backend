@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.appmaster.routes.dto

import com.appmaster.domain.model.entity.ModerationAuditLog
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UpdateModerationStatusRequest(
    val status: String,
    val note: String = ""
)

@Serializable
data class SkipModerationRequest(
    val note: String = ""
)

@Serializable
data class ModerationAuditLogResponse(
    val id: String,
    val reviewer: String,
    val action: String,
    val targetType: String,
    val targetId: String,
    val targetTitle: String,
    val note: String,
    @SerialName("timestamp")
    val createdAt: String
)

fun ModerationAuditLog.toDto(): ModerationAuditLogResponse =
    ModerationAuditLogResponse(
        id = id,
        reviewer = reviewer,
        action = action.id,
        targetType = targetType.id,
        targetId = targetId,
        targetTitle = targetTitle,
        note = note,
        createdAt = createdAt.toString()
    )
