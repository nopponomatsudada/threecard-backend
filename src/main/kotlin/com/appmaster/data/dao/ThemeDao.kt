@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.appmaster.data.dao

import com.appmaster.data.dbQuery
import com.appmaster.data.entity.ThemesTable
import com.appmaster.domain.model.entity.Theme
import com.appmaster.domain.model.valueobject.ThemeId
import com.appmaster.domain.model.valueobject.UserId
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll

class ThemeDao {

    suspend fun findAll(tagId: String?, areaCode: String?, limit: Int, offset: Int): List<Theme> = dbQuery {
        val query = ThemesTable.selectAll().apply {
            val conditions = buildList {
                if (tagId != null) add(ThemesTable.tagId eq tagId)
                if (areaCode != null) add(ThemesTable.areaCode eq areaCode)
            }
            if (conditions.isNotEmpty()) {
                where { conditions.reduce { acc, op -> acc and op } }
            }
        }
        query
            .orderBy(ThemesTable.createdAt to SortOrder.DESC)
            .limit(limit).offset(offset.toLong())
            .map { it.toTheme() }
    }

    suspend fun findById(id: ThemeId): Theme? = dbQuery {
        ThemesTable.selectAll()
            .where { ThemesTable.id eq id.value }
            .singleOrNull()
            ?.toTheme()
    }

    suspend fun insert(theme: Theme): Theme = dbQuery {
        ThemesTable.insert {
            it[id] = theme.id.value
            it[title] = theme.title
            it[description] = theme.description
            it[tagId] = theme.tagId
            it[areaCode] = theme.areaCode
            it[authorId] = theme.authorId.value
            it[createdAt] = theme.createdAt
        }
        theme
    }

    private fun ResultRow.toTheme(): Theme = Theme(
        id = ThemeId(this[ThemesTable.id]),
        title = this[ThemesTable.title],
        description = this[ThemesTable.description],
        tagId = this[ThemesTable.tagId],
        areaCode = this[ThemesTable.areaCode],
        authorId = UserId(this[ThemesTable.authorId]),
        createdAt = this[ThemesTable.createdAt]
    )
}
