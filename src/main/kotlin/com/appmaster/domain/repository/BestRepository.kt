package com.appmaster.domain.repository

import com.appmaster.domain.model.entity.Best
import com.appmaster.domain.model.entity.BestWithTheme
import com.appmaster.domain.model.valueobject.BestId
import com.appmaster.domain.model.valueobject.ThemeId
import com.appmaster.domain.model.valueobject.UserId

interface BestRepository {
    suspend fun findById(id: BestId): Best?
    suspend fun findByThemeId(themeId: ThemeId, limit: Int, offset: Int): List<Best>
    suspend fun findByAuthorId(authorId: UserId, limit: Int, offset: Int): List<Best>
    suspend fun findByAuthorIdWithTheme(authorId: UserId, limit: Int, offset: Int): List<BestWithTheme>
    suspend fun findByAuthorAndTheme(authorId: UserId, themeId: ThemeId): Best?
    suspend fun countByAuthorId(authorId: UserId): Int
    suspend fun save(best: Best): Best
}
