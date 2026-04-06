package com.appmaster.domain.model.entity

import com.appmaster.domain.model.`enum`.Rank
import com.appmaster.domain.model.valueobject.BestId
import com.appmaster.domain.model.valueobject.ThemeId
import com.appmaster.domain.model.valueobject.UserId
import java.util.UUID
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
data class Best(
    val id: BestId,
    val themeId: ThemeId,
    val authorId: UserId,
    val items: List<BestItem>,
    val createdAt: Instant
) {
    companion object {
        fun create(
            themeId: ThemeId,
            authorId: UserId,
            items: List<ItemInput>
        ): Best {
            val now = Instant.fromEpochMilliseconds(System.currentTimeMillis())
            val bestId = BestId.generate()
            return Best(
                id = bestId,
                themeId = themeId,
                authorId = authorId,
                items = items.map { input ->
                    BestItem(
                        id = UUID.randomUUID().toString(),
                        bestId = bestId,
                        rank = input.rank,
                        name = input.name,
                        description = input.description
                    )
                },
                createdAt = now
            )
        }
    }

    data class ItemInput(
        val rank: Rank,
        val name: String,
        val description: String?
    )
}
