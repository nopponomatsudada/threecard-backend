package com.appmaster.domain.usecase.moderation

import com.appmaster.domain.error.DomainError
import com.appmaster.domain.error.DomainException
import com.appmaster.domain.model.entity.Best
import com.appmaster.domain.model.`enum`.ModerationStatus
import com.appmaster.domain.model.valueobject.BestId
import com.appmaster.domain.repository.BestRepository
import com.appmaster.domain.repository.ModerationRepository

class ReviewBestUseCase(
    private val moderationRepository: ModerationRepository,
    private val bestRepository: BestRepository
) {
    data class Params(
        val bestId: BestId,
        val status: ModerationStatus
    )

    suspend operator fun invoke(params: Params): Best {
        if (!params.status.isReviewDecision) {
            throw DomainException(DomainError.InvalidModerationStatus)
        }

        val updated = moderationRepository.updateBestStatus(params.bestId, params.status)
        if (!updated) throw DomainException(DomainError.NotFound("ベスト"))

        return bestRepository.findById(params.bestId)!!
    }
}
