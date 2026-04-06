@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.appmaster.data.entity

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.timestamp

object BestsTable : Table("bests") {
    val id = varchar("id", 36)
    val themeId = varchar("theme_id", 36).references(ThemesTable.id).index()
    val authorId = varchar("author_id", 36).references(UsersTable.id)
    val createdAt = timestamp("created_at").index()

    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex("uq_bests_theme_author", themeId, authorId)
    }
}
