@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.appmaster.routes.dto

import com.appmaster.domain.model.Area
import com.appmaster.domain.model.entity.Theme
import com.appmaster.domain.model.`enum`.Tag
import kotlinx.serialization.Serializable

@Serializable
data class ThemeResponse(
    val id: String,
    val title: String,
    val description: String?,
    val tagId: String,
    val tagColor: String,
    val areaCode: String?,
    val areaName: String?,
    val authorId: String,
    val bestCount: Int,
    val moderationStatus: String,
    val createdAt: String
)

@Serializable
data class CreateThemeRequest(
    val title: String,
    val description: String? = null,
    val tagId: String,
    val areaCode: String? = null
)

fun Theme.toDto(bestCount: Int = 0): ThemeResponse {
    val area = areaCode?.let { Area.findByCode(it) }
    return ThemeResponse(
        id = id.value,
        title = title,
        description = description,
        tagId = tagId,
        tagColor = Tag.fromId(tagId)?.color ?: "#9E9E9E",
        areaCode = areaCode,
        areaName = area?.nameEn,
        authorId = authorId.value,
        bestCount = bestCount,
        moderationStatus = moderationStatus.id,
        createdAt = createdAt.toString()
    )
}
