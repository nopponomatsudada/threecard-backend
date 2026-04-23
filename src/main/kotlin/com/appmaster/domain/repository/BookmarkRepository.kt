package com.appmaster.domain.repository

import com.appmaster.domain.model.entity.Bookmark
import com.appmaster.domain.model.entity.DiscoverCard
import com.appmaster.domain.model.valueobject.BestId
import com.appmaster.domain.model.valueobject.UserId

interface BookmarkRepository {
    suspend fun save(bookmark: Bookmark): Bookmark
    suspend fun deleteByUserIdAndBestId(userId: UserId, bestId: BestId)
    suspend fun findByUserIdAndBestId(userId: UserId, bestId: BestId): Bookmark?
    suspend fun findByUserId(userId: UserId, limit: Int, offset: Int): List<DiscoverCard>
    suspend fun countByUserId(userId: UserId): Int
    suspend fun findBookmarkedBestIds(userId: UserId, bestIds: List<String>): Set<String>
}
