package com.appmaster.routes.dto

import com.appmaster.domain.model.`enum`.Tag
import kotlinx.serialization.Serializable

@Serializable
data class TagResponse(
    val id: String,
    val label: String
)

fun Tag.toDto() = TagResponse(id = id, label = label)

private val cachedTagDtos: List<TagResponse> = Tag.entries.map { it.toDto() }
fun allTagDtos(): List<TagResponse> = cachedTagDtos
