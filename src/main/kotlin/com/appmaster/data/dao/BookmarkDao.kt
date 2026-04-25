@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.appmaster.data.dao

import com.appmaster.data.dbQuery
import com.appmaster.data.entity.BestItemsTable
import com.appmaster.data.entity.BestsTable
import com.appmaster.data.entity.BookmarksTable
import com.appmaster.data.entity.ThemesTable
import com.appmaster.data.entity.UsersTable
import com.appmaster.domain.model.entity.Bookmark
import com.appmaster.domain.model.entity.BookmarkedItem
import com.appmaster.domain.model.enum.ModerationStatus
import com.appmaster.domain.model.enum.Rank
import com.appmaster.domain.model.enum.Tag
import com.appmaster.domain.model.valueobject.BestId
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
            it[bestItemId] = bookmark.bestItemId
            it[createdAt] = bookmark.createdAt
        }
        bookmark
    }

    suspend fun deleteByUserIdAndBestItemId(userId: UserId, bestItemId: String): Unit = dbQuery {
        BookmarksTable.deleteWhere {
            (BookmarksTable.userId eq userId.value) and (BookmarksTable.bestItemId eq bestItemId)
        }
        Unit
    }

    suspend fun findByUserIdAndBestItemId(userId: UserId, bestItemId: String): Bookmark? = dbQuery {
        BookmarksTable.selectAll()
            .where { (BookmarksTable.userId eq userId.value) and (BookmarksTable.bestItemId eq bestItemId) }
            .singleOrNull()?.toBookmark()
    }

    suspend fun findByUserId(userId: UserId, limit: Int, offset: Int): List<BookmarkedItem> = dbQuery {
        val rows = BookmarksTable
            .join(BestItemsTable, JoinType.INNER, BookmarksTable.bestItemId, BestItemsTable.id)
            .join(BestsTable, JoinType.INNER, BestItemsTable.bestId, BestsTable.id)
            .join(ThemesTable, JoinType.INNER, BestsTable.themeId, ThemesTable.id)
            .join(UsersTable, JoinType.INNER, BestsTable.authorId, UsersTable.id)
            .selectAll()
            .where {
                (BookmarksTable.userId eq userId.value) and
                    (BestsTable.moderationStatus eq ModerationStatus.APPROVED.id) and
                    (ThemesTable.moderationStatus eq ModerationStatus.APPROVED.id)
            }
            .orderBy(BookmarksTable.createdAt to SortOrder.DESC)
            .limit(limit).offset(offset.toLong())
            .toList()

        rows.map { it.toBookmarkedItem() }
    }

    suspend fun countByUserId(userId: UserId): Int = dbQuery {
        BookmarksTable
            .join(BestItemsTable, JoinType.INNER, BookmarksTable.bestItemId, BestItemsTable.id)
            .join(BestsTable, JoinType.INNER, BestItemsTable.bestId, BestsTable.id)
            .join(ThemesTable, JoinType.INNER, BestsTable.themeId, ThemesTable.id)
            .selectAll()
            .where {
                (BookmarksTable.userId eq userId.value) and
                    (BestsTable.moderationStatus eq ModerationStatus.APPROVED.id) and
                    (ThemesTable.moderationStatus eq ModerationStatus.APPROVED.id)
            }
            .count().toInt()
    }

    suspend fun findBookmarkedBestItemIds(userId: UserId, bestItemIds: List<String>): Set<String> = dbQuery {
        BookmarksTable
            .join(BestItemsTable, JoinType.INNER, BookmarksTable.bestItemId, BestItemsTable.id)
            .join(BestsTable, JoinType.INNER, BestItemsTable.bestId, BestsTable.id)
            .join(ThemesTable, JoinType.INNER, BestsTable.themeId, ThemesTable.id)
            .selectAll()
            .where {
                (BookmarksTable.userId eq userId.value) and
                    (BookmarksTable.bestItemId inList bestItemIds) and
                    (BestsTable.moderationStatus eq ModerationStatus.APPROVED.id) and
                    (ThemesTable.moderationStatus eq ModerationStatus.APPROVED.id)
            }
            .map { it[BookmarksTable.bestItemId] }
            .toSet()
    }

    private fun ResultRow.toBookmark(): Bookmark = Bookmark(
        id = this[BookmarksTable.id],
        userId = UserId(this[BookmarksTable.userId]),
        bestItemId = this[BookmarksTable.bestItemId],
        createdAt = this[BookmarksTable.createdAt]
    )

    private fun ResultRow.toBookmarkedItem(): BookmarkedItem {
        val tagIdValue = this[ThemesTable.tagId]
        val tag = Tag.fromId(tagIdValue)
        return BookmarkedItem(
            id = this[BestItemsTable.id],
            bestId = BestId(this[BestItemsTable.bestId]),
            rank = Rank.fromValue(this[BestItemsTable.rank])!!,
            name = this[BestItemsTable.name],
            description = this[BestItemsTable.description],
            themeId = this[ThemesTable.id],
            themeTitle = this[ThemesTable.title],
            tagId = tagIdValue,
            tagName = tag?.label ?: tagIdValue,
            authorDisplayId = this[UsersTable.displayId],
            createdAt = this[BookmarksTable.createdAt]
        )
    }
}
