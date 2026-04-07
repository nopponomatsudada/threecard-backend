package com.appmaster.domain.usecase.discover

import com.appmaster.domain.model.entity.DiscoverCard
import com.appmaster.domain.model.valueobject.UserId
import com.appmaster.domain.repository.DiscoverRepository

class GetRandomCardsUseCase(
    private val discoverRepository: DiscoverRepository
) {
    data class Params(
        val userId: UserId,
        val tagId: String?,
        val limit: Int = 20
    )

    suspend operator fun invoke(params: Params): List<DiscoverCard> {
        val clampedLimit = params.limit.coerceIn(1, 50)
        return discoverRepository.getRandomCards(params.userId, params.tagId, clampedLimit)
    }
}
