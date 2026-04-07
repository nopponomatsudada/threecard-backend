package com.appmaster.domain.repository

import com.appmaster.domain.model.entity.Best
import com.appmaster.domain.model.entity.Collection
import com.appmaster.domain.model.entity.CollectionCard
import com.appmaster.domain.model.valueobject.BestId
import com.appmaster.domain.model.valueobject.CollectionId
import com.appmaster.domain.model.valueobject.UserId

interface CollectionRepository {
    suspend fun findByUserId(userId: UserId): List<Collection>
    suspend fun findById(id: CollectionId): Collection?
    suspend fun countByUserId(userId: UserId): Int
    suspend fun save(collection: Collection): Collection
    suspend fun deleteById(id: CollectionId)
    suspend fun addCard(collectionCard: CollectionCard): CollectionCard
    suspend fun findCard(collectionId: CollectionId, bestId: BestId): CollectionCard?
    suspend fun removeCard(collectionId: CollectionId, bestId: BestId)
    suspend fun getCards(collectionId: CollectionId, limit: Int, offset: Int): List<Best>
    suspend fun getCardCounts(collectionIds: List<String>): Map<String, Int>
}
