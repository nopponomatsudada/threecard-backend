package com.appmaster.data.repository

import com.appmaster.data.dao.BookmarkDao
import com.appmaster.domain.model.entity.Bookmark
import com.appmaster.domain.model.entity.DiscoverCard
import com.appmaster.domain.model.valueobject.BestId
import com.appmaster.domain.model.valueobject.UserId
import com.appmaster.domain.repository.BookmarkRepository

class BookmarkRepositoryImpl(
    private val dao: BookmarkDao
) : BookmarkRepository {

    override suspend fun save(bookmark: Bookmark): Bookmark =
        dao.insert(bookmark)

    override suspend fun deleteByUserIdAndBestId(userId: UserId, bestId: BestId) =
        dao.deleteByUserIdAndBestId(userId, bestId)

    override suspend fun findByUserIdAndBestId(userId: UserId, bestId: BestId): Bookmark? =
        dao.findByUserIdAndBestId(userId, bestId)

    override suspend fun findByUserId(userId: UserId, limit: Int, offset: Int): List<DiscoverCard> =
        dao.findByUserId(userId, limit, offset)

    override suspend fun countByUserId(userId: UserId): Int =
        dao.countByUserId(userId)

    override suspend fun findBookmarkedBestIds(userId: UserId, bestIds: List<String>): Set<String> =
        dao.findBookmarkedBestIds(userId, bestIds)
}
