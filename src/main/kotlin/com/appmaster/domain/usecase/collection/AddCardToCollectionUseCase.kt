package com.appmaster.domain.usecase.collection

import com.appmaster.domain.error.DomainError
import com.appmaster.domain.error.DomainException
import com.appmaster.domain.model.entity.CollectionCard
import com.appmaster.domain.model.valueobject.BestId
import com.appmaster.domain.model.valueobject.CollectionId
import com.appmaster.domain.model.valueobject.UserId
import com.appmaster.domain.repository.BestRepository
import com.appmaster.domain.repository.CollectionRepository

class AddCardToCollectionUseCase(
    private val collectionRepository: CollectionRepository,
    private val bestRepository: BestRepository
) {
    data class Params(
        val collectionId: CollectionId,
        val bestId: BestId,
        val userId: UserId
    )

    suspend operator fun invoke(params: Params): CollectionCard {
        collectionRepository.findOwnedCollection(params.collectionId, params.userId)

        bestRepository.findById(params.bestId)
            ?: throw DomainException(DomainError.NotFound("カード"))

        val existing = collectionRepository.findCard(params.collectionId, params.bestId)
        if (existing != null) {
            throw DomainException(DomainError.DuplicateBookmark)
        }

        val collectionCard = CollectionCard.create(
            collectionId = params.collectionId,
            bestId = params.bestId
        )
        return collectionRepository.addCard(collectionCard)
    }
}
