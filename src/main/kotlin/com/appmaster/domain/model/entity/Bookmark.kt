@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.appmaster.domain.model.entity

import com.appmaster.domain.model.valueobject.BestId
import com.appmaster.domain.model.valueobject.UserId
import java.util.UUID
import kotlin.time.Instant

data class Bookmark(
    val id: String,
    val userId: UserId,
    val bestId: BestId,
    val createdAt: Instant
) {
    companion object {
        fun create(userId: UserId, bestId: BestId): Bookmark {
            val now = Instant.fromEpochMilliseconds(System.currentTimeMillis())
            return Bookmark(
                id = UUID.randomUUID().toString(),
                userId = userId,
                bestId = bestId,
                createdAt = now
            )
        }
    }
}
