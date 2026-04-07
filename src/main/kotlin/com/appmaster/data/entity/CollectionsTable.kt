@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.appmaster.data.entity

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.timestamp

object CollectionsTable : Table("collections") {
    val id = varchar("id", 36)
    val userId = varchar("user_id", 36).references(UsersTable.id).index()
    val title = varchar("title", 255)
    val createdAt = timestamp("created_at").index()

    override val primaryKey = PrimaryKey(id)
}
