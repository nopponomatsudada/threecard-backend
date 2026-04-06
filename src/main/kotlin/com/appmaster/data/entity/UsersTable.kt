@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.appmaster.data.entity

import com.appmaster.domain.model.`enum`.Plan
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.timestamp

object UsersTable : Table("users") {
    val id = varchar("id", 36)
    val deviceId = varchar("device_id", 255).uniqueIndex()
    val displayId = varchar("display_id", 10).uniqueIndex()
    val plan = enumerationByName<Plan>("plan", 10)
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")

    override val primaryKey = PrimaryKey(id)
}
