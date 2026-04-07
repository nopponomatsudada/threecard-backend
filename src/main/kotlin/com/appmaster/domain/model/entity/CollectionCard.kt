package com.appmaster.domain.model.entity

import com.appmaster.domain.model.valueobject.BestId
import com.appmaster.domain.model.valueobject.CollectionId
import java.util.UUID

data class CollectionCard(
    val id: String,
    val collectionId: CollectionId,
    val bestId: BestId
) {
    companion object {
        fun create(collectionId: CollectionId, bestId: BestId): CollectionCard {
            return CollectionCard(
                id = UUID.randomUUID().toString(),
                collectionId = collectionId,
                bestId = bestId
            )
        }
    }
}
