package com.appmaster.domain.model.entity

import com.appmaster.domain.model.valueobject.ThemeId
import com.appmaster.domain.model.valueobject.UserId
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
data class Theme(
    val id: ThemeId,
    val title: String,
    val description: String?,
    val tagId: String,
    val areaCode: String?,
    val authorId: UserId,
    val createdAt: Instant
) {
    companion object {
        fun create(
            title: String,
            description: String?,
            tagId: String,
            areaCode: String?,
            authorId: UserId
        ): Theme {
            val now = Instant.fromEpochMilliseconds(System.currentTimeMillis())
            return Theme(
                id = ThemeId.generate(),
                title = title,
                description = description,
                tagId = tagId,
                areaCode = areaCode,
                authorId = authorId,
                createdAt = now
            )
        }
    }
}
