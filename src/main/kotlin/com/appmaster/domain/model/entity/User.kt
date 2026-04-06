package com.appmaster.domain.model.entity

import com.appmaster.domain.model.`enum`.Plan
import com.appmaster.domain.model.valueobject.DisplayId
import com.appmaster.domain.model.valueobject.UserId
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
data class User(
    val id: UserId,
    val deviceId: String,
    val displayId: DisplayId,
    val plan: Plan,
    val createdAt: Instant,
    val updatedAt: Instant
) {
    companion object {
        fun create(deviceId: String): User {
            val now = Instant.fromEpochMilliseconds(System.currentTimeMillis())
            return User(
                id = UserId.generate(),
                deviceId = deviceId,
                displayId = DisplayId.generate(),
                plan = Plan.FREE,
                createdAt = now,
                updatedAt = now
            )
        }
    }
}
