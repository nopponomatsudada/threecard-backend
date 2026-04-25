@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.appmaster.data.dao

import com.appmaster.data.dbQuery
import com.appmaster.data.entity.BestsTable
import com.appmaster.data.entity.ModerationAuditLogsTable
import com.appmaster.data.entity.ThemesTable
import com.appmaster.data.entity.UsersTable
import com.appmaster.domain.model.entity.Best
import com.appmaster.domain.model.entity.ModerationAuditLog
import com.appmaster.domain.model.entity.Theme
import com.appmaster.domain.model.`enum`.ModerationAction
import com.appmaster.domain.model.`enum`.ModerationStatus
import com.appmaster.domain.model.`enum`.ModerationTargetType
import com.appmaster.domain.model.valueobject.BestId
import com.appmaster.domain.model.valueobject.ThemeId
import com.appmaster.domain.repository.ModerationRepository
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import java.util.UUID
import kotlin.time.Instant

class ModerationDao {

    suspend fun findBestsByStatus(status: ModerationStatus, limit: Int, offset: Int): List<Best> = dbQuery {
        val bests = BestsTable
            .join(UsersTable, JoinType.INNER, BestsTable.authorId, UsersTable.id)
            .selectAll()
            .where { BestsTable.moderationStatus eq status.id }
            .orderBy(BestsTable.createdAt to SortOrder.DESC)
            .limit(limit).offset(offset.toLong())
            .map { it.toBestWithoutItems() }

        attachItemsToBests(bests)
    }

    suspend fun findThemesByStatus(status: ModerationStatus, limit: Int, offset: Int): List<Theme> = dbQuery {
        ThemesTable.selectAll()
            .where { ThemesTable.moderationStatus eq status.id }
            .orderBy(ThemesTable.createdAt to SortOrder.DESC)
            .limit(limit).offset(offset.toLong())
            .map { it.toTheme() }
    }

    suspend fun reviewBest(bestId: BestId, status: ModerationStatus, audit: ModerationRepository.AuditMetadata): Best? = dbQuery {
        val count = BestsTable.update({ BestsTable.id eq bestId.value }) {
            it[moderationStatus] = status.id
        }
        if (count == 0) {
            return@dbQuery null
        }

        val best = findBestById(bestId) ?: return@dbQuery null
        insertAuditLog(
            action = when (status) {
                ModerationStatus.APPROVED -> ModerationAction.APPROVE
                ModerationStatus.REJECTED -> ModerationAction.REJECT
                else -> error("reviewBest only accepts review decisions")
            },
            targetType = ModerationTargetType.BEST,
            targetId = best.id.value,
            targetTitle = best.toAuditTitle(),
            audit = audit
        )
        best
    }

    suspend fun reviewTheme(themeId: ThemeId, status: ModerationStatus, audit: ModerationRepository.AuditMetadata): Theme? = dbQuery {
        val count = ThemesTable.update({ ThemesTable.id eq themeId.value }) {
            it[moderationStatus] = status.id
        }
        if (count == 0) {
            return@dbQuery null
        }

        val theme = findThemeById(themeId) ?: return@dbQuery null
        insertAuditLog(
            action = when (status) {
                ModerationStatus.APPROVED -> ModerationAction.APPROVE
                ModerationStatus.REJECTED -> ModerationAction.REJECT
                else -> error("reviewTheme only accepts review decisions")
            },
            targetType = ModerationTargetType.THEME,
            targetId = theme.id.value,
            targetTitle = theme.title,
            audit = audit
        )
        theme
    }

    suspend fun skipBest(bestId: BestId, audit: ModerationRepository.AuditMetadata): Best? = dbQuery {
        val best = findBestById(bestId) ?: return@dbQuery null
        insertAuditLog(
            action = ModerationAction.SKIP,
            targetType = ModerationTargetType.BEST,
            targetId = best.id.value,
            targetTitle = best.toAuditTitle(),
            audit = audit
        )
        best
    }

    suspend fun skipTheme(themeId: ThemeId, audit: ModerationRepository.AuditMetadata): Theme? = dbQuery {
        val theme = findThemeById(themeId) ?: return@dbQuery null
        insertAuditLog(
            action = ModerationAction.SKIP,
            targetType = ModerationTargetType.THEME,
            targetId = theme.id.value,
            targetTitle = theme.title,
            audit = audit
        )
        theme
    }

    suspend fun findAuditLogs(limit: Int, offset: Int, action: ModerationAction?): List<ModerationAuditLog> = dbQuery {
        ModerationAuditLogsTable
            .selectAll()
            .apply {
                if (action != null) {
                    where { ModerationAuditLogsTable.action eq action.id }
                }
            }
            .orderBy(ModerationAuditLogsTable.createdAt to SortOrder.DESC)
            .limit(limit).offset(offset.toLong())
            .map { row ->
                ModerationAuditLog(
                    id = row[ModerationAuditLogsTable.id],
                    reviewer = row[ModerationAuditLogsTable.reviewer],
                    action = ModerationAction.fromId(row[ModerationAuditLogsTable.action]) ?: ModerationAction.SKIP,
                    targetType = ModerationTargetType.fromId(row[ModerationAuditLogsTable.targetType]) ?: ModerationTargetType.THEME,
                    targetId = row[ModerationAuditLogsTable.targetId],
                    targetTitle = row[ModerationAuditLogsTable.targetTitle],
                    note = row[ModerationAuditLogsTable.note],
                    createdAt = row[ModerationAuditLogsTable.createdAt]
                )
            }
    }

    private fun findBestById(bestId: BestId): Best? {
        val best = BestsTable
            .join(UsersTable, JoinType.INNER, BestsTable.authorId, UsersTable.id)
            .selectAll()
            .where { BestsTable.id eq bestId.value }
            .limit(1)
            .firstOrNull()
            ?.toBestWithoutItems()
            ?: return null

        return attachItemsToBests(listOf(best)).firstOrNull()
    }

    private fun findThemeById(themeId: ThemeId): Theme? =
        ThemesTable
            .selectAll()
            .where { ThemesTable.id eq themeId.value }
            .limit(1)
            .firstOrNull()
            ?.toTheme()

    private fun insertAuditLog(
        action: ModerationAction,
        targetType: ModerationTargetType,
        targetId: String,
        targetTitle: String,
        audit: ModerationRepository.AuditMetadata
    ) {
        ModerationAuditLogsTable.insert {
            it[id] = UUID.randomUUID().toString()
            it[reviewer] = audit.reviewer
            it[ModerationAuditLogsTable.action] = action.id
            it[ModerationAuditLogsTable.targetType] = targetType.id
            it[ModerationAuditLogsTable.targetId] = targetId
            it[ModerationAuditLogsTable.targetTitle] = targetTitle.take(140)
            it[note] = audit.note.take(500)
            it[createdAt] = Instant.fromEpochMilliseconds(System.currentTimeMillis())
        }
    }
}

private fun Best.toAuditTitle(): String =
    items.firstOrNull()?.let { first ->
        if (items.size > 1) {
            "${first.name} ほか ${items.size - 1} 件"
        } else {
            first.name
        }
    } ?: "Best 投稿"
