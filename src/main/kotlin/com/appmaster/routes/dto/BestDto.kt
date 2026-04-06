@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.appmaster.routes.dto

import com.appmaster.domain.model.entity.Best
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
    items = items.map { item ->
        BestItemResponse(
            rank = item.rank.value,
            name = item.name,
            description = item.description
        )
    },
    createdAt = createdAt.toString()
)
