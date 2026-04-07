@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.appmaster.data.dao

import com.appmaster.data.dbQuery
import com.appmaster.data.entity.BestItemsTable
import com.appmaster.data.entity.BestsTable
import com.appmaster.data.entity.CollectionCardsTable
import com.appmaster.data.entity.CollectionsTable
import com.appmaster.data.entity.ThemesTable
import com.appmaster.data.entity.UsersTable
import com.appmaster.domain.model.entity.DiscoverCard
import com.appmaster.domain.model.`enum`.Tag
import com.appmaster.domain.model.valueobject.BestId
import com.appmaster.domain.model.valueobject.ThemeId
import com.appmaster.domain.model.valueobject.UserId
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.selectAll

class DiscoverDao {

    suspend fun getRandomCards(userId: UserId, tagId: String?, limit: Int): List<DiscoverCard> = dbQuery {
        var query = BestsTable
            .join(ThemesTable, JoinType.INNER, BestsTable.themeId, ThemesTable.id)
            .join(UsersTable, JoinType.INNER, BestsTable.authorId, UsersTable.id)
            .selectAll()

        if (tagId != null) {
            query = query.where { ThemesTable.tagId eq tagId }
        }

        val rows = query
            .orderBy(org.jetbrains.exposed.v1.core.Random() to SortOrder.ASC)
            .limit(limit)
            .toList()

        if (rows.isEmpty()) return@dbQuery emptyList()

        val bestIds = rows.map { it[BestsTable.id] }

        val itemsByBestId = BestItemsTable.selectAll()
            .where { BestItemsTable.bestId inList bestIds }
            .map { it.toBestItem() }
            .groupBy { it.bestId.value }

        val bookmarkedBestIds = CollectionCardsTable
            .join(CollectionsTable, JoinType.INNER, CollectionCardsTable.collectionId, CollectionsTable.id)
            .selectAll()
            .where { (CollectionCardsTable.bestId inList bestIds) and (CollectionsTable.userId eq userId.value) }
            .map { it[CollectionCardsTable.bestId] }
            .toSet()

        rows.map { row ->
            val bestId = row[BestsTable.id]
            val tagIdValue = row[ThemesTable.tagId]
            val tag = Tag.fromId(tagIdValue)

            DiscoverCard(
                id = BestId(bestId),
                themeId = ThemeId(row[BestsTable.themeId]),
                themeTitle = row[ThemesTable.title],
                tagName = tag?.label ?: tagIdValue,
                authorDisplayId = row[UsersTable.displayId],
                items = itemsByBestId[bestId]?.sortedBy { it.rank.value } ?: emptyList(),
                isBookmarked = bestId in bookmarkedBestIds,
                createdAt = row[BestsTable.createdAt]
            )
        }
    }
}
