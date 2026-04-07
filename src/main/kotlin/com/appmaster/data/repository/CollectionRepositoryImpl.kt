package com.appmaster.data.repository

import com.appmaster.data.dao.CollectionDao
import com.appmaster.domain.model.entity.Best
import com.appmaster.domain.model.entity.Collection
import com.appmaster.domain.model.entity.CollectionCard
import com.appmaster.domain.model.valueobject.BestId
import com.appmaster.domain.model.valueobject.CollectionId
import com.appmaster.domain.model.valueobject.UserId
import com.appmaster.domain.repository.CollectionRepository

class CollectionRepositoryImpl(
    private val dao: CollectionDao
) : CollectionRepository {

    override suspend fun findByUserId(userId: UserId): List<Collection> =
        dao.findByUserId(userId)

    override suspend fun findById(id: CollectionId): Collection? =
        dao.findById(id)

    override suspend fun countByUserId(userId: UserId): Int =
        dao.countByUserId(userId)

    override suspend fun save(collection: Collection): Collection =
        dao.insert(collection)

    override suspend fun deleteById(id: CollectionId) =
        dao.deleteById(id)

    override suspend fun addCard(collectionCard: CollectionCard): CollectionCard =
        dao.addCard(collectionCard)

    override suspend fun findCard(collectionId: CollectionId, bestId: BestId): CollectionCard? =
        dao.findCard(collectionId, bestId)

    override suspend fun removeCard(collectionId: CollectionId, bestId: BestId) =
        dao.removeCard(collectionId, bestId)

    override suspend fun getCards(collectionId: CollectionId, limit: Int, offset: Int): List<Best> =
        dao.getCards(collectionId, limit, offset)

    override suspend fun getCardCounts(collectionIds: List<String>): Map<String, Int> =
        dao.getCardCounts(collectionIds)
}
