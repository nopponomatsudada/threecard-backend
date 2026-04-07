package com.appmaster.data.entity

import org.jetbrains.exposed.v1.core.Table

object CollectionCardsTable : Table("collection_cards") {
    val id = varchar("id", 36)
    val collectionId = varchar("collection_id", 36).references(CollectionsTable.id).index()
    val bestId = varchar("best_id", 36).references(BestsTable.id)

    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex("uq_collection_cards_collection_best", collectionId, bestId)
    }
}
