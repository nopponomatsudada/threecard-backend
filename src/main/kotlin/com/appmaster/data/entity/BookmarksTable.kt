@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.appmaster.data.entity

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.timestamp

object BookmarksTable : Table("bookmarks") {
    val id = varchar("id", 36)
    val userId = varchar("user_id", 36).references(UsersTable.id).index()
    val bestItemId = varchar("best_item_id", 36).references(BestItemsTable.id)
    val createdAt = timestamp("created_at").index()

    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex("uq_bookmarks_user_best_item", userId, bestItemId)
    }
}
