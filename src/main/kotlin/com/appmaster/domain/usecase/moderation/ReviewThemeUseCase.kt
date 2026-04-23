package com.appmaster.domain.usecase.moderation

import com.appmaster.domain.error.DomainError
import com.appmaster.domain.error.DomainException
import com.appmaster.domain.model.entity.Theme
import com.appmaster.domain.model.`enum`.ModerationStatus
import com.appmaster.domain.model.valueobject.ThemeId
import com.appmaster.domain.repository.ModerationRepository
import com.appmaster.domain.repository.ThemeRepository

class ReviewThemeUseCase(
    private val moderationRepository: ModerationRepository,
    private val themeRepository: ThemeRepository
) {
    data class Params(
        val themeId: ThemeId,
        val status: ModerationStatus
    )

    suspend operator fun invoke(params: Params): Theme {
        if (!params.status.isReviewDecision) {
            throw DomainException(DomainError.InvalidModerationStatus)
        }

        val updated = moderationRepository.updateThemeStatus(params.themeId, params.status)
        if (!updated) throw DomainException(DomainError.NotFound("テーマ"))

        return themeRepository.findThemeOnly(params.themeId)!!
    }
}
