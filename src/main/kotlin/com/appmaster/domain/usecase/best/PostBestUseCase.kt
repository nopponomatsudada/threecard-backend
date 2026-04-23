package com.appmaster.domain.usecase.best

import com.appmaster.domain.error.DomainError
import com.appmaster.domain.error.DomainException
import com.appmaster.domain.model.entity.Best
import com.appmaster.domain.model.`enum`.Rank
import com.appmaster.domain.model.valueobject.ThemeId
import com.appmaster.domain.model.valueobject.UserId
import com.appmaster.domain.repository.BestRepository
import com.appmaster.domain.repository.ThemeRepository

class PostBestUseCase(
    private val bestRepository: BestRepository,
    private val themeRepository: ThemeRepository
) {
    companion object {
        const val MAX_ITEM_NAME_LENGTH = 50
    }

    data class Params(
        val themeId: ThemeId,
        val authorId: UserId,
        val items: List<ItemParam>
    )

    data class ItemParam(
        val rank: Int,
        val name: String,
        val description: String?
    )

    suspend operator fun invoke(params: Params): Best {
        themeRepository.findThemeOnly(params.themeId)
            ?: throw DomainException(DomainError.NotFound("テーマ"))

        if (params.items.isEmpty() || params.items.size > 3) {
            throw DomainException(DomainError.ValidationError("アイテムは1〜3件で入力してください"))
        }

        val ranks = mutableSetOf<Int>()
        val itemInputs = params.items.map { item ->
            val rank = Rank.fromValue(item.rank)
                ?: throw DomainException(DomainError.ValidationError("順位は1〜3で入力してください"))

            if (!ranks.add(item.rank)) {
                throw DomainException(DomainError.ValidationError("順位が重複しています"))
            }

            val trimmedName = item.name.trim()
            if (trimmedName.isEmpty()) {
                throw DomainException(DomainError.BestItemNameRequired)
            }

            if (trimmedName.length > MAX_ITEM_NAME_LENGTH) {
                throw DomainException(DomainError.BestItemNameTooLong)
            }

            if (item.description != null && item.description.length > 140) {
                throw DomainException(DomainError.BestItemDescriptionTooLong)
            }

            Best.ItemInput(rank = rank, name = trimmedName, description = item.description)
        }

        val existing = bestRepository.findByAuthorAndTheme(params.authorId, params.themeId)
        if (existing != null) {
            throw DomainException(DomainError.AlreadyPosted)
        }

        val best = Best.create(
            themeId = params.themeId,
            authorId = params.authorId,
            items = itemInputs
        )
        return bestRepository.save(best)
    }
}
