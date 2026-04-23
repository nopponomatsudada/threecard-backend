package com.appmaster.domain.repository

import com.appmaster.domain.model.entity.Theme
import com.appmaster.domain.model.entity.ThemeWithBestCount
import com.appmaster.domain.model.valueobject.ThemeId

interface ThemeRepository {
    suspend fun findAll(tagId: String?, areaCode: String?, limit: Int, offset: Int): List<ThemeWithBestCount>
    suspend fun findById(id: ThemeId): ThemeWithBestCount?
    suspend fun save(theme: Theme): Theme
}
