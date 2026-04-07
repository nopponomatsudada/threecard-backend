@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.appmaster.data.dao

import com.appmaster.data.dbQuery
import com.appmaster.data.entity.BestsTable
import com.appmaster.data.entity.CollectionCardsTable
import com.appmaster.data.entity.CollectionsTable
import com.appmaster.domain.model.entity.Best
import com.appmaster.domain.model.entity.Collection
import com.appmaster.domain.model.entity.CollectionCard
import com.appmaster.domain.model.valueobject.BestId
import com.appmaster.domain.model.valueobject.CollectionId
import com.appmaster.domain.model.valueobject.UserId
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll

class CollectionDao {

    suspend fun findByUserId(userId: UserId): List<Collection> = dbQuery {
        CollectionsTable.selectAll()
            .where { CollectionsTable.userId eq userId.value }
            .orderBy(CollectionsTable.createdAt to SortOrder.DESC)
            .map { it.toCollection() }
    }

    suspend fun findById(id: CollectionId): Collection? = dbQuery {
        CollectionsTable.selectAll()
            .where { CollectionsTable.id eq id.value }
            .singleOrNull()?.toCollection()
    }

    suspend fun countByUserId(userId: UserId): Int = dbQuery {
        CollectionsTable.selectAll()
            .where { CollectionsTable.userId eq userId.value }
            .count().toInt()
    }

    suspend fun insert(collection: Collection): Collection = dbQuery {
        CollectionsTable.insert {
            it[id] = collection.id.value
            it[userId] = collection.userId.value
            it[title] = collection.title
            it[createdAt] = collection.createdAt
        }
        collection
    }

    suspend fun deleteById(id: CollectionId): Unit = dbQuery {
        CollectionCardsTable.deleteWhere { collectionId eq id.value }
        CollectionsTable.deleteWhere { CollectionsTable.id eq id.value }
        Unit
    }

    suspend fun addCard(collectionCard: CollectionCard): CollectionCard = dbQuery {
        CollectionCardsTable.insert {
            it[id] = collectionCard.id
            it[collectionId] = collectionCard.collectionId.value
            it[bestId] = collectionCard.bestId.value
        }
        collectionCard
    }

    suspend fun findCard(collectionId: CollectionId, bestId: BestId): CollectionCard? = dbQuery {
        CollectionCardsTable.selectAll()
            .where { (CollectionCardsTable.collectionId eq collectionId.value) and (CollectionCardsTable.bestId eq bestId.value) }
            .singleOrNull()?.toCollectionCard()
    }

    suspend fun removeCard(collectionId: CollectionId, bestId: BestId): Unit = dbQuery {
        CollectionCardsTable.deleteWhere {
            (CollectionCardsTable.collectionId eq collectionId.value) and (CollectionCardsTable.bestId eq bestId.value)
        }
        Unit
    }

    suspend fun getCards(collectionId: CollectionId, limit: Int, offset: Int): List<Best> = dbQuery {
        val bests = BestsTable
            .join(CollectionCardsTable, JoinType.INNER, BestsTable.id, CollectionCardsTable.bestId)
            .selectAll()
            .where { CollectionCardsTable.collectionId eq collectionId.value }
            .orderBy(BestsTable.createdAt to SortOrder.DESC)
            .limit(limit).offset(offset.toLong())
            .map { it.toBestWithoutItems() }

        attachItemsToBests(bests)
    }

    suspend fun getCardCounts(collectionIds: List<String>): Map<String, Int> = dbQuery {
        if (collectionIds.isEmpty()) return@dbQuery emptyMap()

        CollectionCardsTable.selectAll()
            .where { CollectionCardsTable.collectionId inList collectionIds }
            .groupBy { it[CollectionCardsTable.collectionId] }
            .mapValues { it.value.size }
    }

    private fun ResultRow.toCollection(): Collection = Collection(
        id = CollectionId(this[CollectionsTable.id]),
        userId = UserId(this[CollectionsTable.userId]),
        title = this[CollectionsTable.title],
        createdAt = this[CollectionsTable.createdAt]
    )

    private fun ResultRow.toCollectionCard(): CollectionCard = CollectionCard(
        id = this[CollectionCardsTable.id],
        collectionId = CollectionId(this[CollectionCardsTable.collectionId]),
        bestId = BestId(this[CollectionCardsTable.bestId])
    )
}
