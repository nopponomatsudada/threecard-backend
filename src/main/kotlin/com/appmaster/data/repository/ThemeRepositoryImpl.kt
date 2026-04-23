package com.appmaster.data.repository

import com.appmaster.data.dao.ThemeDao
import com.appmaster.domain.model.entity.Theme
import com.appmaster.domain.model.entity.ThemeWithBestCount
import com.appmaster.domain.model.valueobject.ThemeId
import com.appmaster.domain.repository.ThemeRepository

class ThemeRepositoryImpl(
    private val dao: ThemeDao
) : ThemeRepository {

    override suspend fun findAll(tagId: String?, areaCode: String?, limit: Int, offset: Int): List<ThemeWithBestCount> =
        dao.findAll(tagId, areaCode, limit, offset)

    override suspend fun findById(id: ThemeId): ThemeWithBestCount? =
        dao.findById(id)

    override suspend fun save(theme: Theme): Theme = dao.insert(theme)
}
