package com.appmaster.domain.usecase.moderation

import com.appmaster.domain.error.DomainError
import com.appmaster.domain.error.DomainException
import com.appmaster.domain.model.entity.Theme
import com.appmaster.domain.model.valueobject.ThemeId
import com.appmaster.domain.repository.ModerationRepository

class SkipThemeUseCase(
    private val moderationRepository: ModerationRepository
) {
    data class Params(
        val themeId: ThemeId,
        val reviewer: String,
        val note: String
    )

    suspend operator fun invoke(params: Params): Theme =
        moderationRepository.skipTheme(
            themeId = params.themeId,
            audit = ModerationRepository.AuditMetadata(
                reviewer = params.reviewer,
                note = params.note
            )
        ) ?: throw DomainException(DomainError.NotFound("テーマ"))
}
