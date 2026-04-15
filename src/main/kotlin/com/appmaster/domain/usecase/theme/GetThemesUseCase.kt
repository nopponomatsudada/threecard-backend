package com.appmaster.domain.usecase.theme

import com.appmaster.domain.model.entity.Theme
import com.appmaster.domain.repository.ThemeRepository

class GetThemesUseCase(
    private val themeRepository: ThemeRepository
) {
    data class Params(
        val tagId: String?,
        val areaCode: String?,
        val limit: Int,
        val offset: Int
    )

    suspend operator fun invoke(params: Params): List<Theme> {
        val clampedLimit = params.limit.coerceIn(1, 50)
        val safeOffset = maxOf(0, params.offset)
        return themeRepository.findAll(params.tagId, params.areaCode, clampedLimit, safeOffset)
    }
}
