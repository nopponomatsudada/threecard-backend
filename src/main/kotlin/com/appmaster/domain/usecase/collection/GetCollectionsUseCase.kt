package com.appmaster.domain.usecase.collection

import com.appmaster.domain.model.entity.CollectionWithCount
import com.appmaster.domain.model.valueobject.UserId
import com.appmaster.domain.repository.CollectionRepository

class GetCollectionsUseCase(
    private val collectionRepository: CollectionRepository
) {
    suspend operator fun invoke(userId: UserId): List<CollectionWithCount> {
        val collections = collectionRepository.findByUserId(userId)
        if (collections.isEmpty()) return emptyList()

        val cardCounts = collectionRepository.getCardCounts(collections.map { it.id.value })
        return collections.map { collection ->
            CollectionWithCount(collection, cardCounts[collection.id.value] ?: 0)
        }
    }
}
