package com.appmaster.domain.usecase.best

import com.appmaster.domain.error.DomainError
import com.appmaster.domain.error.DomainException
import com.appmaster.domain.model.entity.Best
import com.appmaster.domain.model.valueobject.ThemeId
import com.appmaster.domain.repository.BestRepository
import com.appmaster.domain.repository.ThemeRepository

class GetBestsByThemeUseCase(
    private val bestRepository: BestRepository,
    private val themeRepository: ThemeRepository
) {
    data class Params(
        val themeId: ThemeId,
        val limit: Int,
        val offset: Int
    )

    suspend operator fun invoke(params: Params): List<Best> {
        themeRepository.findById(params.themeId)?.theme
            ?: throw DomainException(DomainError.NotFound("テーマ"))

        val clampedLimit = params.limit.coerceIn(1, 50)
        val safeOffset = maxOf(0, params.offset)
        return bestRepository.findByThemeId(params.themeId, clampedLimit, safeOffset)
    }
}
