package com.appmaster.data.repository

import com.appmaster.data.dao.ModerationDao
import com.appmaster.domain.model.entity.Best
import com.appmaster.domain.model.entity.Theme
import com.appmaster.domain.model.`enum`.ModerationStatus
import com.appmaster.domain.model.valueobject.BestId
import com.appmaster.domain.model.valueobject.ThemeId
import com.appmaster.domain.repository.ModerationRepository

class ModerationRepositoryImpl(
    private val dao: ModerationDao
) : ModerationRepository {

    override suspend fun findBestsByStatus(status: ModerationStatus, limit: Int, offset: Int): List<Best> =
        dao.findBestsByStatus(status, limit, offset)

    override suspend fun findThemesByStatus(status: ModerationStatus, limit: Int, offset: Int): List<Theme> =
        dao.findThemesByStatus(status, limit, offset)

    override suspend fun updateBestStatus(bestId: BestId, status: ModerationStatus): Boolean =
        dao.updateBestStatus(bestId, status)

    override suspend fun updateThemeStatus(themeId: ThemeId, status: ModerationStatus): Boolean =
        dao.updateThemeStatus(themeId, status)
}
