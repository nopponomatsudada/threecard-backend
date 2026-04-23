@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.appmaster.data.dao

import com.appmaster.data.dbQuery
import com.appmaster.data.entity.BestsTable
import com.appmaster.data.entity.ThemesTable
import com.appmaster.domain.model.entity.Theme
import com.appmaster.domain.model.`enum`.ModerationStatus
import com.appmaster.domain.model.valueobject.ThemeId
import com.appmaster.domain.model.valueobject.UserId
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll

class ThemeDao {

    suspend fun findAll(tagId: String?, areaCode: String?, limit: Int, offset: Int): List<Pair<Theme, Int>> = dbQuery {
        val query = ThemesTable.selectAll().apply {
            val conditions = buildList {
                add(ThemesTable.moderationStatus eq ModerationStatus.APPROVED.id)
                if (tagId != null) add(ThemesTable.tagId eq tagId)
                if (areaCode != null) add(ThemesTable.areaCode eq areaCode)
            }
            where { conditions.reduce { acc, op -> acc and op } }
        }
        val themes = query
            .orderBy(ThemesTable.createdAt to SortOrder.DESC)
            .limit(limit).offset(offset.toLong())
            .map { it.toTheme() }

        if (themes.isEmpty()) return@dbQuery emptyList()

        val themeIds = themes.map { it.id.value }
        val bestCounts = BestsTable.selectAll()
            .where { (BestsTable.themeId inList themeIds) and (BestsTable.moderationStatus eq ModerationStatus.APPROVED.id) }
            .groupBy { it[BestsTable.themeId] }
            .mapValues { it.value.size }

        themes.map { theme -> theme to (bestCounts[theme.id.value] ?: 0) }
    }

    suspend fun findById(id: ThemeId): Pair<Theme, Int>? = dbQuery {
        val theme = ThemesTable.selectAll()
            .where { ThemesTable.id eq id.value }
            .singleOrNull()
            ?.toTheme() ?: return@dbQuery null

        val bestCount = BestsTable.selectAll()
            .where { (BestsTable.themeId eq id.value) and (BestsTable.moderationStatus eq ModerationStatus.APPROVED.id) }
            .count().toInt()

        theme to bestCount
    }

    suspend fun insert(theme: Theme): Theme = dbQuery {
        ThemesTable.insert {
            it[ThemesTable.id] = theme.id.value
            it[title] = theme.title
            it[description] = theme.description
            it[ThemesTable.tagId] = theme.tagId
            it[areaCode] = theme.areaCode
            it[authorId] = theme.authorId.value
            it[moderationStatus] = theme.moderationStatus.id
            it[createdAt] = theme.createdAt
        }
        theme
    }

}

internal fun ResultRow.toTheme(): Theme = Theme(
    id = ThemeId(this[ThemesTable.id]),
    title = this[ThemesTable.title],
    description = this[ThemesTable.description],
    tagId = this[ThemesTable.tagId],
    areaCode = this[ThemesTable.areaCode],
    authorId = UserId(this[ThemesTable.authorId]),
    moderationStatus = ModerationStatus.fromId(this[ThemesTable.moderationStatus]) ?: ModerationStatus.PENDING,
    createdAt = this[ThemesTable.createdAt]
)
