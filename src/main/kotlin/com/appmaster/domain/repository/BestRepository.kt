package com.appmaster.domain.repository

import com.appmaster.domain.model.entity.Best
import com.appmaster.domain.model.valueobject.ThemeId
import com.appmaster.domain.model.valueobject.UserId

interface BestRepository {
    suspend fun findByThemeId(themeId: ThemeId, limit: Int, offset: Int): List<Best>
    suspend fun findByAuthorAndTheme(authorId: UserId, themeId: ThemeId): Best?
    suspend fun save(best: Best): Best
}
