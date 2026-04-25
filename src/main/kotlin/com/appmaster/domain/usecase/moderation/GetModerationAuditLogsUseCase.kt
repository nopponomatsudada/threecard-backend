package com.appmaster.domain.usecase.moderation

import com.appmaster.domain.model.entity.ModerationAuditLog
import com.appmaster.domain.model.`enum`.ModerationAction
import com.appmaster.domain.repository.ModerationRepository

class GetModerationAuditLogsUseCase(
    private val moderationRepository: ModerationRepository
) {
    suspend operator fun invoke(limit: Int, offset: Int, action: ModerationAction? = null): List<ModerationAuditLog> =
        moderationRepository.findAuditLogs(limit, offset, action)
}
