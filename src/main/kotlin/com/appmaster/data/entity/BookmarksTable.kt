@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.appmaster.data.entity

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.timestamp

object BookmarksTable : Table("bookmarks") {
    val id = varchar("id", 36)
    val userId = varchar("user_id", 36).references(UsersTable.id).index()
    val bestId = varchar("best_id", 36).references(BestsTable.id)
    val createdAt = timestamp("created_at").index()

    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex("uq_bookmarks_user_best", userId, bestId)
    }
}
