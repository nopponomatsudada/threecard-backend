package com.appmaster.data.repository

import com.appmaster.data.dao.BookmarkDao
import com.appmaster.domain.model.entity.Bookmark
import com.appmaster.domain.model.entity.BookmarkedItem
import com.appmaster.domain.model.valueobject.UserId
import com.appmaster.domain.repository.BookmarkRepository

class BookmarkRepositoryImpl(
    private val dao: BookmarkDao
) : BookmarkRepository {

    override suspend fun save(bookmark: Bookmark): Bookmark =
        dao.insert(bookmark)

    override suspend fun deleteByUserIdAndBestItemId(userId: UserId, bestItemId: String) =
        dao.deleteByUserIdAndBestItemId(userId, bestItemId)

    override suspend fun findByUserIdAndBestItemId(userId: UserId, bestItemId: String): Bookmark? =
        dao.findByUserIdAndBestItemId(userId, bestItemId)

    override suspend fun findByUserId(userId: UserId, limit: Int, offset: Int): List<BookmarkedItem> =
        dao.findByUserId(userId, limit, offset)

    override suspend fun countByUserId(userId: UserId): Int =
        dao.countByUserId(userId)

    override suspend fun findBookmarkedBestItemIds(userId: UserId, bestItemIds: List<String>): Set<String> =
        dao.findBookmarkedBestItemIds(userId, bestItemIds)
}
