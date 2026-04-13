@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.appmaster.routes.dto

import com.appmaster.domain.model.entity.DiscoverCard
import com.appmaster.domain.model.`enum`.Tag
import kotlinx.serialization.Serializable

@Serializable
data class DiscoverCardResponse(
    val id: String,
    val themeId: String,
    val themeTitle: String,
    val tagName: String,
    val tagColor: String,
    val authorDisplayId: String,
    val items: List<BestItemResponse>,
    val isBookmarked: Boolean,
    val createdAt: String
)

fun DiscoverCard.toDto() = DiscoverCardResponse(
    id = id.value,
    themeId = themeId.value,
    themeTitle = themeTitle,
    tagName = tagName,
    tagColor = Tag.fromId(tagId)?.color ?: "#9E9E9E",
    authorDisplayId = authorDisplayId,
    items = items.map { item ->
        BestItemResponse(
            rank = item.rank.value,
            name = item.name,
            description = item.description
        )
    },
    isBookmarked = isBookmarked,
    createdAt = createdAt.toString()
)
