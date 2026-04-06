package com.appmaster.data.entity

import org.jetbrains.exposed.v1.core.Table

object BestItemsTable : Table("best_items") {
    val id = varchar("id", 36)
    val bestId = varchar("best_id", 36).references(BestsTable.id).index()
    val rank = integer("rank")
    val name = varchar("name", 255)
    val description = varchar("description", 140).nullable()

    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex("uq_best_items_best_rank", bestId, rank)
    }
}
