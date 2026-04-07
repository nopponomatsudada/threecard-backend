package com.appmaster.domain.repository

import com.appmaster.domain.model.entity.DiscoverCard
import com.appmaster.domain.model.valueobject.UserId

interface DiscoverRepository {
    suspend fun getRandomCards(userId: UserId, tagId: String?, limit: Int): List<DiscoverCard>
}
