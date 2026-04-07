package com.appmaster.data.repository

import com.appmaster.data.dao.DiscoverDao
import com.appmaster.domain.model.entity.DiscoverCard
import com.appmaster.domain.model.valueobject.UserId
import com.appmaster.domain.repository.DiscoverRepository

class DiscoverRepositoryImpl(
    private val dao: DiscoverDao
) : DiscoverRepository {

    override suspend fun getRandomCards(userId: UserId, tagId: String?, limit: Int): List<DiscoverCard> =
        dao.getRandomCards(userId, tagId, limit)
}
