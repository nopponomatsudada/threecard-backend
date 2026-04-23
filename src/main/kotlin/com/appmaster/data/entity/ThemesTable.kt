@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.appmaster.data.entity

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.timestamp

object ThemesTable : Table("themes") {
    val id = varchar("id", 36)
    val title = varchar("title", 100)
    val description = varchar("description", 140).nullable()
    val tagId = varchar("tag_id", 50).index()
    val areaCode = varchar("area_code", 10).nullable()
    val authorId = varchar("author_id", 36).references(UsersTable.id)
    val moderationStatus = varchar("moderation_status", 20).default("approved").index()
    val createdAt = timestamp("created_at").index()

    override val primaryKey = PrimaryKey(id)

    init {
        index(false, tagId, createdAt)
    }
}
