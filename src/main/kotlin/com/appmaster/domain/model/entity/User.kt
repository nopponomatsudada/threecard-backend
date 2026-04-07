package com.appmaster.domain.model.entity

import com.appmaster.domain.model.`enum`.Plan
import com.appmaster.domain.model.valueobject.DisplayId
import com.appmaster.domain.model.valueobject.UserId
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
data class User(
    val id: UserId,
    val deviceId: String,
    /** Nullable to accommodate users created before the device-secret rollout.
     *  A null hash means: on next /auth/device, treat as bootstrap and persist
     *  a freshly issued secret. */
    val deviceSecretHash: String?,
    val displayId: DisplayId,
    val plan: Plan,
    val createdAt: Instant,
    val updatedAt: Instant
) {
    companion object {
        fun create(
            deviceId: String,
            deviceSecretHash: String,
            clock: Clock = Clock.System
        ): User {
            val now = clock.now()
            return User(
                id = UserId.generate(),
                deviceId = deviceId,
                deviceSecretHash = deviceSecretHash,
                displayId = DisplayId.generate(),
                plan = Plan.FREE,
                createdAt = now,
                updatedAt = now
            )
        }
    }
}
