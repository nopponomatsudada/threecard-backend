package com.appmaster.domain.model.entity

import com.appmaster.domain.model.valueobject.CollectionId
import com.appmaster.domain.model.valueobject.UserId
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
data class Collection(
    val id: CollectionId,
    val userId: UserId,
    val title: String,
    val createdAt: Instant
) {
    companion object {
        fun create(userId: UserId, title: String): Collection {
            val now = Instant.fromEpochMilliseconds(System.currentTimeMillis())
            return Collection(
                id = CollectionId.generate(),
                userId = userId,
                title = title,
                createdAt = now
            )
        }
    }
}
