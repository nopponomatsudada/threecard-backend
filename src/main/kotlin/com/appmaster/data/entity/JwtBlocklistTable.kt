@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.appmaster.data.entity

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.timestamp

object JwtBlocklistTable : Table("jwt_blocklist") {
    val jti = varchar("jti", 36)
    val expiresAt = timestamp("expires_at").index()

    override val primaryKey = PrimaryKey(jti)
}
