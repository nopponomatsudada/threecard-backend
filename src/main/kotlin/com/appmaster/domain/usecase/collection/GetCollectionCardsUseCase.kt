package com.appmaster.domain.usecase.collection

import com.appmaster.domain.model.entity.Best
import com.appmaster.domain.model.valueobject.CollectionId
import com.appmaster.domain.model.valueobject.Pagination
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
        collectionRepository.assertOwnership(params.collectionId, params.userId)

        val pagination = Pagination.of(params.limit, params.offset)
        return collectionRepository.getCards(params.collectionId, pagination.limit, pagination.offset)
    }
}
