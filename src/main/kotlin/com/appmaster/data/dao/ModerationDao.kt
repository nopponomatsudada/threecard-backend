@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.appmaster.data.dao

import com.appmaster.data.dbQuery
import com.appmaster.data.entity.BestsTable
import com.appmaster.data.entity.ThemesTable
import com.appmaster.data.entity.UsersTable
import com.appmaster.domain.model.entity.Best
import com.appmaster.domain.model.entity.Theme
import com.appmaster.domain.model.`enum`.ModerationStatus
import com.appmaster.domain.model.valueobject.BestId
import com.appmaster.domain.model.valueobject.ThemeId
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update

class ModerationDao {

    suspend fun findBestsByStatus(status: ModerationStatus, limit: Int, offset: Int): List<Best> = dbQuery {
        val bests = BestsTable
            .join(UsersTable, JoinType.INNER, BestsTable.authorId, UsersTable.id)
            .selectAll()
            .where { BestsTable.moderationStatus eq status.id }
            .orderBy(BestsTable.createdAt to SortOrder.DESC)
            .limit(limit).offset(offset.toLong())
            .map { it.toBestWithoutItems() }

        attachItemsToBests(bests)
    }

    suspend fun findThemesByStatus(status: ModerationStatus, limit: Int, offset: Int): List<Theme> = dbQuery {
        ThemesTable.selectAll()
            .where { ThemesTable.moderationStatus eq status.id }
            .orderBy(ThemesTable.createdAt to SortOrder.DESC)
            .limit(limit).offset(offset.toLong())
            .map { it.toTheme() }
    }

    suspend fun updateBestStatus(bestId: BestId, status: ModerationStatus): Boolean = dbQuery {
        val count = BestsTable.update({ BestsTable.id eq bestId.value }) {
            it[moderationStatus] = status.id
        }
        count > 0
    }

    suspend fun updateThemeStatus(themeId: ThemeId, status: ModerationStatus): Boolean = dbQuery {
        val count = ThemesTable.update({ ThemesTable.id eq themeId.value }) {
            it[moderationStatus] = status.id
        }
        count > 0
    }
}
