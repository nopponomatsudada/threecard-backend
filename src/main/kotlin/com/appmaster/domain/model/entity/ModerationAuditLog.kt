package com.appmaster.domain.model.entity

import com.appmaster.domain.model.`enum`.ModerationAction
import com.appmaster.domain.model.`enum`.ModerationTargetType
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
data class ModerationAuditLog(
    val id: String,
    val reviewer: String,
    val action: ModerationAction,
    val targetType: ModerationTargetType,
    val targetId: String,
    val targetTitle: String,
    val note: String,
    val createdAt: Instant
)
