package com.appmaster.domain.usecase.moderation

import com.appmaster.domain.model.entity.Best
import com.appmaster.domain.model.entity.Theme
import com.appmaster.domain.model.`enum`.ModerationStatus
import com.appmaster.domain.repository.ModerationRepository

class GetPendingContentsUseCase(
    private val moderationRepository: ModerationRepository
) {
    suspend fun getBests(status: ModerationStatus, limit: Int, offset: Int): List<Best> =
        moderationRepository.findBestsByStatus(status, limit, offset)

    suspend fun getThemes(status: ModerationStatus, limit: Int, offset: Int): List<Theme> =
        moderationRepository.findThemesByStatus(status, limit, offset)
}
