package com.appmaster.data.repository

import com.appmaster.data.dao.ModerationDao
import com.appmaster.domain.model.entity.Best
import com.appmaster.domain.model.entity.ModerationAuditLog
import com.appmaster.domain.model.entity.Theme
import com.appmaster.domain.model.`enum`.ModerationAction
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

    override suspend fun reviewBest(bestId: BestId, status: ModerationStatus, audit: ModerationRepository.AuditMetadata): Best? =
        dao.reviewBest(bestId, status, audit)

    override suspend fun reviewTheme(themeId: ThemeId, status: ModerationStatus, audit: ModerationRepository.AuditMetadata): Theme? =
        dao.reviewTheme(themeId, status, audit)

    override suspend fun skipBest(bestId: BestId, audit: ModerationRepository.AuditMetadata): Best? =
        dao.skipBest(bestId, audit)

    override suspend fun skipTheme(themeId: ThemeId, audit: ModerationRepository.AuditMetadata): Theme? =
        dao.skipTheme(themeId, audit)

    override suspend fun findAuditLogs(limit: Int, offset: Int, action: ModerationAction?): List<ModerationAuditLog> =
        dao.findAuditLogs(limit, offset, action)
}
