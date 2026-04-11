@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.appmaster.routes.dto

import com.appmaster.domain.model.entity.Best
import com.appmaster.domain.model.entity.BestWithTheme
import kotlinx.serialization.Serializable

@Serializable
data class PostBestRequest(
    val items: List<PostBestItemRequest>
)

@Serializable
data class PostBestItemRequest(
    val rank: Int,
    val name: String,
    val description: String? = null
)

@Serializable
data class BestResponse(
    val id: String,
    val themeId: String,
    val authorId: String,
    val authorDisplayId: String,
    val items: List<BestItemResponse>,
    val createdAt: String
)

@Serializable
data class BestItemResponse(
    val rank: Int,
    val name: String,
    val description: String?
)

fun Best.toDto() = BestResponse(
    id = id.value,
    themeId = themeId.value,
    authorId = authorId.value,
    authorDisplayId = authorDisplayId,
    items = items.map { item ->
        BestItemResponse(
            rank = item.rank.value,
            name = item.name,
            description = item.description
        )
    },
    createdAt = createdAt.toString()
)

@Serializable
data class BestWithThemeResponse(
    val id: String,
    val themeId: String,
    val themeTitle: String,
    val tagId: String,
    val authorId: String,
    val authorDisplayId: String,
    val items: List<BestItemResponse>,
    val createdAt: String
)

fun BestWithTheme.toDto() = BestWithThemeResponse(
    id = best.id.value,
    themeId = best.themeId.value,
    themeTitle = themeTitle,
    tagId = tagId,
    authorId = best.authorId.value,
    authorDisplayId = best.authorDisplayId,
    items = best.items.map { item ->
        BestItemResponse(
            rank = item.rank.value,
            name = item.name,
            description = item.description
        )
    },
    createdAt = best.createdAt.toString()
)
