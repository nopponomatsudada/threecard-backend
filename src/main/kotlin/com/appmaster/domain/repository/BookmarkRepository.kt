package com.appmaster.domain.repository

import com.appmaster.domain.model.entity.Bookmark
import com.appmaster.domain.model.entity.BookmarkedItem
import com.appmaster.domain.model.valueobject.UserId

interface BookmarkRepository {
    suspend fun save(bookmark: Bookmark): Bookmark
    suspend fun deleteByUserIdAndBestItemId(userId: UserId, bestItemId: String)
    suspend fun findByUserIdAndBestItemId(userId: UserId, bestItemId: String): Bookmark?
    suspend fun findByUserId(userId: UserId, limit: Int, offset: Int): List<BookmarkedItem>
    suspend fun countByUserId(userId: UserId): Int
    suspend fun findBookmarkedBestItemIds(userId: UserId, bestItemIds: List<String>): Set<String>
}
