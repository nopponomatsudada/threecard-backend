@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.appmaster.data.dao

import com.appmaster.data.dbQuery
import com.appmaster.data.entity.BestItemsTable
import com.appmaster.data.entity.BestsTable
import com.appmaster.domain.model.entity.Best
import com.appmaster.domain.model.entity.BestItem
import com.appmaster.domain.model.`enum`.Rank
import com.appmaster.domain.model.valueobject.BestId
import com.appmaster.domain.model.valueobject.ThemeId
import com.appmaster.domain.model.valueobject.UserId
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll

class BestDao {

    suspend fun findByThemeId(themeId: ThemeId, limit: Int, offset: Int): List<Best> = dbQuery {
        val bests = BestsTable.selectAll()
            .where { BestsTable.themeId eq themeId.value }
            .orderBy(BestsTable.createdAt to SortOrder.DESC)
            .limit(limit).offset(offset.toLong())
            .map { it.toBestWithoutItems() }

        if (bests.isEmpty()) return@dbQuery emptyList()

        val bestIds = bests.map { it.id.value }
        val itemsByBestId = BestItemsTable.selectAll()
            .where { BestItemsTable.bestId inList bestIds }
            .map { it.toBestItem() }
            .groupBy { it.bestId.value }

        bests.map { best ->
            best.copy(items = itemsByBestId[best.id.value]?.sortedBy { it.rank.value } ?: emptyList())
        }
    }

    suspend fun findByAuthorAndTheme(authorId: UserId, themeId: ThemeId): Best? = dbQuery {
        val bestRow = BestsTable.selectAll()
            .where { (BestsTable.authorId eq authorId.value) and (BestsTable.themeId eq themeId.value) }
            .singleOrNull() ?: return@dbQuery null

        val best = bestRow.toBestWithoutItems()
        val items = BestItemsTable.selectAll()
            .where { BestItemsTable.bestId eq best.id.value }
            .map { it.toBestItem() }
            .sortedBy { it.rank.value }

        best.copy(items = items)
    }

    suspend fun insert(best: Best): Best = dbQuery {
        BestsTable.insert {
            it[id] = best.id.value
            it[themeId] = best.themeId.value
            it[authorId] = best.authorId.value
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

        best
    }

    private fun ResultRow.toBestWithoutItems(): Best = Best(
        id = BestId(this[BestsTable.id]),
        themeId = ThemeId(this[BestsTable.themeId]),
        authorId = UserId(this[BestsTable.authorId]),
        items = emptyList(),
        createdAt = this[BestsTable.createdAt]
    )

    private fun ResultRow.toBestItem(): BestItem = BestItem(
        id = this[BestItemsTable.id],
        bestId = BestId(this[BestItemsTable.bestId]),
        rank = Rank.fromValue(this[BestItemsTable.rank])!!,
        name = this[BestItemsTable.name],
        description = this[BestItemsTable.description]
    )
}
