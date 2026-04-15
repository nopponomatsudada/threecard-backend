package com.appmaster.domain.repository

import com.appmaster.domain.model.entity.Theme
import com.appmaster.domain.model.valueobject.ThemeId

interface ThemeRepository {
    suspend fun findAll(tagId: String?, location: String?, limit: Int, offset: Int): List<Theme>
    suspend fun findById(id: ThemeId): Theme?
    suspend fun save(theme: Theme): Theme
}
