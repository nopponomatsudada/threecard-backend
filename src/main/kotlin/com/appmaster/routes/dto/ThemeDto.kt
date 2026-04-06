@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.appmaster.routes.dto

import com.appmaster.domain.model.entity.Theme
import kotlinx.serialization.Serializable

@Serializable
data class ThemeResponse(
    val id: String,
    val title: String,
    val description: String?,
    val tagId: String,
    val authorId: String,
    val bestCount: Int,
    val createdAt: String
)

@Serializable
data class CreateThemeRequest(
    val title: String,
    val description: String? = null,
    val tagId: String
)

fun Theme.toDto(bestCount: Int = 0) = ThemeResponse(
    id = id.value,
    title = title,
    description = description,
    tagId = tagId,
    authorId = authorId.value,
    bestCount = bestCount,
    createdAt = createdAt.toString()
)
