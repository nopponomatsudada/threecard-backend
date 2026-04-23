@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.appmaster.routes.dto

import com.appmaster.domain.model.entity.Bookmark
import kotlinx.serialization.Serializable

@Serializable
data class AddBookmarkRequest(
    val bestId: String
)

@Serializable
data class BookmarkResponse(
    val bestId: String,
    val createdAt: String
)

fun Bookmark.toDto() = BookmarkResponse(
    bestId = bestId.value,
    createdAt = createdAt.toString()
)
