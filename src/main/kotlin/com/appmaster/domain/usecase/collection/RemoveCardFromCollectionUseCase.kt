package com.appmaster.domain.usecase.collection

import com.appmaster.domain.error.DomainError
import com.appmaster.domain.error.DomainException
import com.appmaster.domain.model.valueobject.BestId
import com.appmaster.domain.model.valueobject.CollectionId
import com.appmaster.domain.model.valueobject.UserId
import com.appmaster.domain.repository.CollectionRepository

class RemoveCardFromCollectionUseCase(
    private val collectionRepository: CollectionRepository
) {
    data class Params(
        val collectionId: CollectionId,
        val bestId: BestId,
        val userId: UserId
    )

    suspend operator fun invoke(params: Params) {
        collectionRepository.findOwnedCollection(params.collectionId, params.userId)

        collectionRepository.findCard(params.collectionId, params.bestId)
            ?: throw DomainException(DomainError.NotFound("カード"))

        collectionRepository.removeCard(params.collectionId, params.bestId)
    }
}
