@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.appmaster.data.entity

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.timestamp

object RefreshTokensTable : Table("refresh_tokens") {
    val id = varchar("id", 36)
    val userId = varchar("user_id", 36).references(UsersTable.id).index()
    // sha256 hex of the plain refresh token (64 chars)
    val tokenHash = varchar("token_hash", 64).uniqueIndex()
    val jti = varchar("jti", 36).uniqueIndex()
    val expiresAt = timestamp("expires_at").index()
    val revokedAt = timestamp("revoked_at").nullable()
    val createdAt = timestamp("created_at")

    override val primaryKey = PrimaryKey(id)
}
