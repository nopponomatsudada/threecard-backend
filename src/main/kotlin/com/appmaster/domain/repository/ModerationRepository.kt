package com.appmaster.domain.repository

import com.appmaster.domain.model.entity.Best
import com.appmaster.domain.model.entity.ModerationAuditLog
import com.appmaster.domain.model.entity.Theme
import com.appmaster.domain.model.`enum`.ModerationAction
import com.appmaster.domain.model.`enum`.ModerationStatus
import com.appmaster.domain.model.valueobject.BestId
import com.appmaster.domain.model.valueobject.ThemeId

interface ModerationRepository {
    data class AuditMetadata(
        val reviewer: String,
        val note: String = ""
    )

    suspend fun findBestsByStatus(status: ModerationStatus, limit: Int, offset: Int): List<Best>
    suspend fun findThemesByStatus(status: ModerationStatus, limit: Int, offset: Int): List<Theme>
    suspend fun reviewBest(bestId: BestId, status: ModerationStatus, audit: AuditMetadata): Best?
    suspend fun reviewTheme(themeId: ThemeId, status: ModerationStatus, audit: AuditMetadata): Theme?
    suspend fun skipBest(bestId: BestId, audit: AuditMetadata): Best?
    suspend fun skipTheme(themeId: ThemeId, audit: AuditMetadata): Theme?
    suspend fun findAuditLogs(limit: Int, offset: Int, action: ModerationAction? = null): List<ModerationAuditLog>
}
