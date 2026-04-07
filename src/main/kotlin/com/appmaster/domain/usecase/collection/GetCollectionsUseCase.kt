package com.appmaster.domain.usecase.collection

import com.appmaster.domain.model.entity.Collection
import com.appmaster.domain.model.valueobject.UserId
import com.appmaster.domain.repository.CollectionRepository

class GetCollectionsUseCase(
    private val collectionRepository: CollectionRepository
) {
    suspend operator fun invoke(userId: UserId): List<Pair<Collection, Int>> {
        val collections = collectionRepository.findByUserId(userId)
        if (collections.isEmpty()) return emptyList()

        val cardCounts = collectionRepository.getCardCounts(collections.map { it.id.value })
        return collections.map { collection ->
            collection to (cardCounts[collection.id.value] ?: 0)
        }
    }
}
