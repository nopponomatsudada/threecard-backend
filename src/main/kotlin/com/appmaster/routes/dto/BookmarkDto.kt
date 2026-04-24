@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.appmaster.routes.dto

import com.appmaster.domain.model.entity.Bookmark
import com.appmaster.domain.model.entity.BookmarkedItem
import com.appmaster.domain.model.enum.Tag
import kotlinx.serialization.Serializable

@Serializable
data class AddBookmarkRequest(
    val bestItemId: String
)

@Serializable
data class BookmarkResponse(
    val bestItemId: String,
    val createdAt: String
)

fun Bookmark.toDto() = BookmarkResponse(
    bestItemId = bestItemId,
    createdAt = createdAt.toString()
)

@Serializable
data class BookmarkedItemResponse(
    val id: String,
    val bestId: String,
    val rank: Int,
    val name: String,
    val description: String?,
    val themeTitle: String,
    val tagName: String,
    val tagColor: String,
    val authorDisplayId: String,
    val createdAt: String
)

fun BookmarkedItem.toDto() = BookmarkedItemResponse(
    id = id,
    bestId = bestId.value,
    rank = rank.value,
    name = name,
    description = description,
    themeTitle = themeTitle,
    tagName = tagName,
    tagColor = Tag.fromId(tagId)?.color ?: "#9E9E9E",
    authorDisplayId = authorDisplayId,
    createdAt = createdAt.toString()
)
