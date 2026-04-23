@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.appmaster.data.dao

import com.appmaster.data.dbQuery
import com.appmaster.data.entity.BestItemsTable
import com.appmaster.data.entity.BestsTable
import com.appmaster.data.entity.BookmarksTable
import com.appmaster.data.entity.ThemesTable
import com.appmaster.data.entity.UsersTable
import com.appmaster.domain.model.entity.Bookmark
import com.appmaster.domain.model.entity.DiscoverCard
import com.appmaster.domain.model.`enum`.Tag
import com.appmaster.domain.model.valueobject.BestId
import com.appmaster.domain.model.valueobject.ThemeId
import com.appmaster.domain.model.valueobject.UserId
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll

class BookmarkDao {

    suspend fun insert(bookmark: Bookmark): Bookmark = dbQuery {
        BookmarksTable.insert {
            it[id] = bookmark.id
            it[userId] = bookmark.userId.value
            it[bestId] = bookmark.bestId.value
            it[createdAt] = bookmark.createdAt
        }
        bookmark
    }

    suspend fun deleteByUserIdAndBestId(userId: UserId, bestId: BestId): Unit = dbQuery {
        BookmarksTable.deleteWhere {
            (BookmarksTable.userId eq userId.value) and (BookmarksTable.bestId eq bestId.value)
        }
        Unit
    }

    suspend fun findByUserIdAndBestId(userId: UserId, bestId: BestId): Bookmark? = dbQuery {
        BookmarksTable.selectAll()
            .where { (BookmarksTable.userId eq userId.value) and (BookmarksTable.bestId eq bestId.value) }
            .singleOrNull()?.toBookmark()
    }

    suspend fun findByUserId(userId: UserId, limit: Int, offset: Int): List<DiscoverCard> = dbQuery {
        val rows = BookmarksTable
            .join(BestsTable, JoinType.INNER, BookmarksTable.bestId, BestsTable.id)
            .join(ThemesTable, JoinType.INNER, BestsTable.themeId, ThemesTable.id)
            .join(UsersTable, JoinType.INNER, BestsTable.authorId, UsersTable.id)
            .selectAll()
            .where { BookmarksTable.userId eq userId.value }
            .orderBy(BookmarksTable.createdAt to SortOrder.DESC)
            .limit(limit).offset(offset.toLong())
            .toList()

        if (rows.isEmpty()) return@dbQuery emptyList()

        val bestIds = rows.map { it[BestsTable.id] }

        val itemsByBestId = BestItemsTable.selectAll()
            .where { BestItemsTable.bestId inList bestIds }
            .map { it.toBestItem() }
            .groupBy { it.bestId.value }

        rows.map { row ->
            val bestId = row[BestsTable.id]
            val tagIdValue = row[ThemesTable.tagId]
            val tag = Tag.fromId(tagIdValue)

            DiscoverCard(
                id = BestId(bestId),
                themeId = ThemeId(row[BestsTable.themeId]),
                themeTitle = row[ThemesTable.title],
                tagId = tagIdValue,
                tagName = tag?.label ?: tagIdValue,
                authorDisplayId = row[UsersTable.displayId],
                items = itemsByBestId[bestId]?.sortedBy { it.rank.value } ?: emptyList(),
                isBookmarked = true,
                createdAt = row[BestsTable.createdAt]
            )
        }
    }

    suspend fun countByUserId(userId: UserId): Int = dbQuery {
        BookmarksTable.selectAll()
            .where { BookmarksTable.userId eq userId.value }
            .count().toInt()
    }

    suspend fun findBookmarkedBestIds(userId: UserId, bestIds: List<String>): Set<String> = dbQuery {
        if (bestIds.isEmpty()) return@dbQuery emptySet()
        BookmarksTable.selectAll()
            .where { (BookmarksTable.userId eq userId.value) and (BookmarksTable.bestId inList bestIds) }
            .map { it[BookmarksTable.bestId] }
            .toSet()
    }

    private fun ResultRow.toBookmark(): Bookmark = Bookmark(
        id = this[BookmarksTable.id],
        userId = UserId(this[BookmarksTable.userId]),
        bestId = BestId(this[BookmarksTable.bestId]),
        createdAt = this[BookmarksTable.createdAt]
    )
}
