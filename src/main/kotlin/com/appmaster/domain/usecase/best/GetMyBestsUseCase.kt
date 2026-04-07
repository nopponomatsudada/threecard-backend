package com.appmaster.domain.usecase.best

import com.appmaster.domain.model.entity.BestWithTheme
import com.appmaster.domain.model.valueobject.UserId
import com.appmaster.domain.repository.BestRepository

class GetMyBestsUseCase(
    private val bestRepository: BestRepository
) {
    data class Params(
        val authorId: UserId,
        val limit: Int,
        val offset: Int
    )

    suspend operator fun invoke(params: Params): List<BestWithTheme> {
        val clampedLimit = params.limit.coerceIn(1, 50)
        val safeOffset = maxOf(0, params.offset)
        return bestRepository.findByAuthorIdWithTheme(params.authorId, clampedLimit, safeOffset)
    }
}
