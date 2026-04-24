@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.appmaster.data.dao

import com.appmaster.data.entity.BestItemsTable
import com.appmaster.data.entity.BestsTable
import com.appmaster.data.entity.ThemesTable
import com.appmaster.data.entity.UsersTable
import com.appmaster.domain.model.entity.Best
import com.appmaster.domain.model.entity.BestItem
import com.appmaster.domain.model.entity.DiscoverCard
import com.appmaster.domain.model.enum.ModerationStatus
import com.appmaster.domain.model.enum.Rank
import com.appmaster.domain.model.enum.Tag
import com.appmaster.domain.model.valueobject.BestId
import com.appmaster.domain.model.valueobject.ThemeId
import com.appmaster.domain.model.valueobject.UserId
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.selectAll

internal fun ResultRow.toBestWithoutItems(): Best = Best(
    id = BestId(this[BestsTable.id]),
    themeId = ThemeId(this[BestsTable.themeId]),
    authorId = UserId(this[BestsTable.authorId]),
    authorDisplayId = this[UsersTable.displayId],
    items = emptyList(),
    forkedFromBestId = this[BestsTable.forkedFromBestId]?.let { BestId(it) },
    moderationStatus = ModerationStatus.fromId(this[BestsTable.moderationStatus]) ?: ModerationStatus.PENDING,
    createdAt = this[BestsTable.createdAt]
)

internal fun ResultRow.toBestItem(): BestItem = BestItem(
    id = this[BestItemsTable.id],
    bestId = BestId(this[BestItemsTable.bestId]),
    rank = Rank.fromValue(this[BestItemsTable.rank])!!,
    name = this[BestItemsTable.name],
    description = this[BestItemsTable.description]
)

internal fun attachItemsToBests(bests: List<Best>): List<Best> {
    if (bests.isEmpty()) return emptyList()
    val bestIds = bests.map { it.id.value }
    val itemsByBestId = fetchItemsByBestIds(bestIds)
    return bests.map { best ->
        best.copy(items = itemsByBestId[best.id.value]?.sortedBy { it.rank.value } ?: emptyList())
    }
}

internal fun fetchItemsByBestIds(bestIds: List<String>): Map<String, List<BestItem>> =
    BestItemsTable.selectAll()
        .where { BestItemsTable.bestId inList bestIds }
        .map { it.toBestItem() }
        .groupBy { it.bestId.value }

internal fun ResultRow.toDiscoverCard(
    itemsByBestId: Map<String, List<BestItem>>,
    bookmarkedBestItemIds: Set<String>
): DiscoverCard {
    val bestId = this[BestsTable.id]
    val tagIdValue = this[ThemesTable.tagId]
    val tag = Tag.fromId(tagIdValue)
    return DiscoverCard(
        id = BestId(bestId),
        themeId = ThemeId(this[BestsTable.themeId]),
        themeTitle = this[ThemesTable.title],
        tagId = tagIdValue,
        tagName = tag?.label ?: tagIdValue,
        authorDisplayId = this[UsersTable.displayId],
        items = itemsByBestId[bestId]?.sortedBy { it.rank.value }?.map { item ->
            item.copy(isBookmarked = item.id in bookmarkedBestItemIds)
        } ?: emptyList(),
        createdAt = this[BestsTable.createdAt]
    )
}
