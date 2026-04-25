@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.appmaster.domain.usecase.best

import com.appmaster.domain.error.DomainError
import com.appmaster.domain.error.DomainException
import com.appmaster.domain.model.entity.Best
import com.appmaster.domain.model.entity.BestItem
import com.appmaster.domain.model.`enum`.ModerationStatus
import com.appmaster.domain.model.valueobject.BestId
import com.appmaster.domain.model.valueobject.ThemeId
import com.appmaster.domain.model.valueobject.UserId
import com.appmaster.domain.repository.BestRepository
import java.util.UUID

class UpdateBestUseCase(
    private val bestRepository: BestRepository,
) {
    data class Params(
        val bestId: BestId,
        val themeId: ThemeId,
        val authorId: UserId,
        val items: List<BestItemParam>,
    )

    suspend operator fun invoke(params: Params): Best {
        val existing = bestRepository.findById(params.bestId)
            ?: throw DomainException(DomainError.NotFound("ベスト"))

        if (existing.themeId != params.themeId) {
            throw DomainException(DomainError.NotFound("ベスト"))
        }

        if (existing.authorId != params.authorId) {
            throw DomainException(DomainError.Forbidden)
        }

        val itemInputs = validateBestItemParams(params.items)

        val newItems = itemInputs.map { input ->
            BestItem(
                id = UUID.randomUUID().toString(),
                bestId = params.bestId,
                rank = input.rank,
                name = input.name,
                description = input.description,
            )
        }

        val updated = existing.copy(
            items = newItems,
            moderationStatus = ModerationStatus.PENDING,
        )
        return bestRepository.update(updated)
    }
}
