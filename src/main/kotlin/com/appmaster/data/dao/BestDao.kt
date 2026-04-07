@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.appmaster.data.dao

import com.appmaster.data.dbQuery
import com.appmaster.data.entity.BestItemsTable
import com.appmaster.data.entity.BestsTable
import com.appmaster.domain.model.entity.Best
import com.appmaster.domain.model.valueobject.BestId
import com.appmaster.domain.model.valueobject.ThemeId
import com.appmaster.domain.model.valueobject.UserId
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll

class BestDao {

    suspend fun findById(id: BestId): Best? = dbQuery {
        val best = BestsTable.selectAll()
            .where { BestsTable.id eq id.value }
            .singleOrNull()?.toBestWithoutItems() ?: return@dbQuery null

        attachItemsToBests(listOf(best)).first()
    }

    suspend fun findByThemeId(themeId: ThemeId, limit: Int, offset: Int): List<Best> = dbQuery {
        val bests = BestsTable.selectAll()
            .where { BestsTable.themeId eq themeId.value }
            .orderBy(BestsTable.createdAt to SortOrder.DESC)
            .limit(limit).offset(offset.toLong())
            .map { it.toBestWithoutItems() }

        attachItemsToBests(bests)
    }

    suspend fun findByAuthorId(authorId: UserId, limit: Int, offset: Int): List<Best> = dbQuery {
        val bests = BestsTable.selectAll()
            .where { BestsTable.authorId eq authorId.value }
            .orderBy(BestsTable.createdAt to SortOrder.DESC)
            .limit(limit).offset(offset.toLong())
            .map { it.toBestWithoutItems() }

        attachItemsToBests(bests)
    }

    suspend fun findByAuthorAndTheme(authorId: UserId, themeId: ThemeId): Best? = dbQuery {
        val best = BestsTable.selectAll()
            .where { (BestsTable.authorId eq authorId.value) and (BestsTable.themeId eq themeId.value) }
            .singleOrNull()?.toBestWithoutItems() ?: return@dbQuery null

        attachItemsToBests(listOf(best)).first()
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
}
