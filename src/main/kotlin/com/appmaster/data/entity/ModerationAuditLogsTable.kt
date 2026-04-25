@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.appmaster.data.entity

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.timestamp

object ModerationAuditLogsTable : Table("moderation_audit_logs") {
    val id = varchar("id", 36)
    val reviewer = varchar("reviewer", 100)
    val action = varchar("action", 20).index()
    val targetType = varchar("target_type", 20).index()
    val targetId = varchar("target_id", 36).index()
    val targetTitle = varchar("target_title", 140)
    val note = varchar("note", 500).default("")
    val createdAt = timestamp("created_at").index()

    override val primaryKey = PrimaryKey(id)
}
