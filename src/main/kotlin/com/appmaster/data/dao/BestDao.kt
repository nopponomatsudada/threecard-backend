@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.appmaster.data.dao

import com.appmaster.data.dbQuery
import com.appmaster.data.entity.BestItemsTable
import com.appmaster.data.entity.BestsTable
import com.appmaster.data.entity.ThemesTable
import com.appmaster.data.entity.UsersTable
import com.appmaster.domain.model.entity.Best
import com.appmaster.domain.model.entity.BestItem
import com.appmaster.domain.model.entity.BestWithTheme
import com.appmaster.domain.model.`enum`.ModerationStatus
import com.appmaster.domain.model.valueobject.BestId
import com.appmaster.domain.model.valueobject.ThemeId
import com.appmaster.domain.model.valueobject.UserId
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll

class BestDao {

    suspend fun findBestItemById(id: String): BestItem? = dbQuery {
        BestItemsTable.selectAll()
            .where { BestItemsTable.id eq id }
            .singleOrNull()?.toBestItem()
    }

    suspend fun findById(id: BestId): Best? = dbQuery {
        val best = BestsTable
            .join(UsersTable, JoinType.INNER, BestsTable.authorId, UsersTable.id)
            .selectAll()
            .where { BestsTable.id eq id.value }
            .singleOrNull()?.toBestWithoutItems() ?: return@dbQuery null

        attachItemsToBests(listOf(best)).first()
    }

    suspend fun findByThemeId(themeId: ThemeId, limit: Int, offset: Int): List<Best> = dbQuery {
        val bests = BestsTable
            .join(UsersTable, JoinType.INNER, BestsTable.authorId, UsersTable.id)
            .selectAll()
            .where { (BestsTable.themeId eq themeId.value) and (BestsTable.moderationStatus eq ModerationStatus.APPROVED.id) }
            .orderBy(BestsTable.createdAt to SortOrder.DESC)
            .limit(limit).offset(offset.toLong())
            .map { it.toBestWithoutItems() }

        attachItemsToBests(bests)
    }

    suspend fun findByAuthorId(authorId: UserId, limit: Int, offset: Int): List<Best> = dbQuery {
        val bests = BestsTable
            .join(UsersTable, JoinType.INNER, BestsTable.authorId, UsersTable.id)
            .selectAll()
            .where { BestsTable.authorId eq authorId.value }
            .orderBy(BestsTable.createdAt to SortOrder.DESC)
            .limit(limit).offset(offset.toLong())
            .map { it.toBestWithoutItems() }

        attachItemsToBests(bests)
    }

    suspend fun findByAuthorIdWithTheme(authorId: UserId, limit: Int, offset: Int): List<BestWithTheme> = dbQuery {
        val rows = BestsTable
            .join(ThemesTable, JoinType.INNER, BestsTable.themeId, ThemesTable.id)
            .join(UsersTable, JoinType.INNER, BestsTable.authorId, UsersTable.id)
            .selectAll()
            .where { BestsTable.authorId eq authorId.value }
            .orderBy(BestsTable.createdAt to SortOrder.DESC)
            .limit(limit).offset(offset.toLong())
            .toList()

        val bests = rows.map { it.toBestWithoutItems() }
        val withItems = attachItemsToBests(bests).associateBy { it.id.value }

        rows.mapNotNull { row ->
            val best = withItems[row[BestsTable.id]] ?: return@mapNotNull null
            BestWithTheme(
                best = best,
                themeTitle = row[ThemesTable.title],
                tagId = row[ThemesTable.tagId],
            )
        }
    }

    suspend fun findByAuthorAndTheme(authorId: UserId, themeId: ThemeId): Best? = dbQuery {
        val best = BestsTable
            .join(UsersTable, JoinType.INNER, BestsTable.authorId, UsersTable.id)
            .selectAll()
            .where { (BestsTable.authorId eq authorId.value) and (BestsTable.themeId eq themeId.value) }
            .singleOrNull()?.toBestWithoutItems() ?: return@dbQuery null

        attachItemsToBests(listOf(best)).first()
    }

    suspend fun countByAuthorId(authorId: UserId): Int = dbQuery {
        BestsTable.selectAll()
            .where { BestsTable.authorId eq authorId.value }
            .count().toInt()
    }

    suspend fun insert(best: Best): Best = dbQuery {
        BestsTable.insert {
            it[id] = best.id.value
            it[themeId] = best.themeId.value
            it[authorId] = best.authorId.value
            it[forkedFromBestId] = best.forkedFromBestId?.value
            it[moderationStatus] = best.moderationStatus.id
            it[createdAt] = best.createdAt
        }

        best.items.forEach { item ->
            BestItemsTable.insert {
                it[id] = item.id
                it[bestId] = best.id.value
                it[rank] = item.rank.value
                it[name] = item.name
                it[description] = item.description
            }
        }

        // Look up author's displayId for the response
        val displayId = UsersTable.selectAll()
            .where { UsersTable.id eq best.authorId.value }
            .single()[UsersTable.displayId]

        best.copy(authorDisplayId = displayId)
    }
}
