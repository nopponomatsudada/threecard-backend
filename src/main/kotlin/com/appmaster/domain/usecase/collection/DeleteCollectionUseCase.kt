package com.appmaster.domain.usecase.collection

import com.appmaster.domain.model.valueobject.CollectionId
import com.appmaster.domain.model.valueobject.UserId
import com.appmaster.domain.repository.CollectionRepository

class DeleteCollectionUseCase(
    private val collectionRepository: CollectionRepository
) {
    data class Params(
        val collectionId: CollectionId,
        val userId: UserId
    )

    suspend operator fun invoke(params: Params) {
        collectionRepository.assertOwnership(params.collectionId, params.userId)
        collectionRepository.deleteById(params.collectionId)
    }
}
