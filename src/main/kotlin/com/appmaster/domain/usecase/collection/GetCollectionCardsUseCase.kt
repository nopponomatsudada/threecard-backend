package com.appmaster.domain.usecase.collection

import com.appmaster.domain.model.entity.Best
import com.appmaster.domain.model.valueobject.CollectionId
import com.appmaster.domain.model.valueobject.UserId
import com.appmaster.domain.repository.CollectionRepository

class GetCollectionCardsUseCase(
    private val collectionRepository: CollectionRepository
) {
    data class Params(
        val collectionId: CollectionId,
        val userId: UserId,
        val limit: Int,
        val offset: Int
    )

    suspend operator fun invoke(params: Params): List<Best> {
        collectionRepository.findOwnedCollection(params.collectionId, params.userId)

        val clampedLimit = params.limit.coerceIn(1, 50)
        val safeOffset = maxOf(0, params.offset)
        return collectionRepository.getCards(params.collectionId, clampedLimit, safeOffset)
    }
}
