@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.appmaster.domain.model.entity

import com.appmaster.domain.model.valueobject.BestId
import com.appmaster.domain.model.valueobject.ThemeId
import kotlin.time.Instant

data class DiscoverCard(
    val id: BestId,
    val themeId: ThemeId,
    val themeTitle: String,
    val tagId: String,
    val tagName: String,
    val authorDisplayId: String,
    val items: List<BestItem>,
    val isBookmarked: Boolean,
    val createdAt: Instant
)
