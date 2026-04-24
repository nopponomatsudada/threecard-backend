@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.appmaster.domain.model.entity

import com.appmaster.domain.model.enum.Rank
import com.appmaster.domain.model.valueobject.BestId
import kotlin.time.Instant

data class BookmarkedItem(
    val id: String,
    val bestId: BestId,
    val rank: Rank,
    val name: String,
    val description: String?,
    val themeTitle: String,
    val tagId: String,
    val tagName: String,
    val authorDisplayId: String,
    val createdAt: Instant
)
