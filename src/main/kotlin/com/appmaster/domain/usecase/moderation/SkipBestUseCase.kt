package com.appmaster.domain.usecase.moderation

import com.appmaster.domain.error.DomainError
import com.appmaster.domain.error.DomainException
import com.appmaster.domain.model.entity.Best
import com.appmaster.domain.model.valueobject.BestId
import com.appmaster.domain.repository.ModerationRepository

class SkipBestUseCase(
    private val moderationRepository: ModerationRepository
) {
    data class Params(
        val bestId: BestId,
        val reviewer: String,
        val note: String
    )

    suspend operator fun invoke(params: Params): Best =
        moderationRepository.skipBest(
            bestId = params.bestId,
            audit = ModerationRepository.AuditMetadata(
                reviewer = params.reviewer,
                note = params.note
            )
        ) ?: throw DomainException(DomainError.NotFound("ベスト"))
}
