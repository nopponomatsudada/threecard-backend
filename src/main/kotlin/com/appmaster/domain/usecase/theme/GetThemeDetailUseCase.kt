package com.appmaster.domain.usecase.theme

import com.appmaster.domain.error.DomainError
import com.appmaster.domain.error.DomainException
import com.appmaster.domain.model.entity.Theme
import com.appmaster.domain.model.valueobject.ThemeId
import com.appmaster.domain.repository.ThemeRepository

class GetThemeDetailUseCase(
    private val themeRepository: ThemeRepository
) {
    suspend operator fun invoke(themeId: ThemeId): Theme =
        themeRepository.findById(themeId)
            ?: throw DomainException(DomainError.NotFound("テーマ"))
}
