package com.appmaster.domain.repository

import com.appmaster.domain.model.entity.Best
import com.appmaster.domain.model.entity.Theme
import com.appmaster.domain.model.`enum`.ModerationStatus
import com.appmaster.domain.model.valueobject.BestId
import com.appmaster.domain.model.valueobject.ThemeId

interface ModerationRepository {
    suspend fun findBestsByStatus(status: ModerationStatus, limit: Int, offset: Int): List<Best>
    suspend fun findThemesByStatus(status: ModerationStatus, limit: Int, offset: Int): List<Theme>
    suspend fun updateBestStatus(bestId: BestId, status: ModerationStatus): Boolean
    suspend fun updateThemeStatus(themeId: ThemeId, status: ModerationStatus): Boolean
}
