package com.appmaster.domain.usecase.theme

import com.appmaster.domain.error.DomainError
import com.appmaster.domain.error.DomainException
import com.appmaster.domain.model.Area
import com.appmaster.domain.model.entity.Theme
import com.appmaster.domain.model.`enum`.Tag
import com.appmaster.domain.model.valueobject.UserId
import com.appmaster.domain.repository.ThemeRepository

class CreateThemeUseCase(
    private val themeRepository: ThemeRepository
) {
    data class Params(
        val title: String,
        val description: String?,
        val tagId: String,
        val areaCode: String?,
        val authorId: UserId
    )

    suspend operator fun invoke(params: Params): Theme {
        if (params.title.isBlank()) {
            throw DomainException(DomainError.ValidationError("タイトルを入力してください"))
        }
        if (params.title.length > 100) {
            throw DomainException(DomainError.ThemeTitleTooLong)
        }
        if (params.description != null && params.description.length > 140) {
            throw DomainException(DomainError.ThemeDescriptionTooLong)
        }
        if (Tag.fromId(params.tagId) == null) {
            throw DomainException(DomainError.TagNotSelected)
        }
        if (params.areaCode != null && Area.findByCode(params.areaCode) == null) {
            throw DomainException(DomainError.ValidationError("無効なエリアコードです: ${params.areaCode}"))
        }

        val theme = Theme.create(
            title = params.title,
            description = params.description,
            tagId = params.tagId,
            areaCode = params.areaCode,
            authorId = params.authorId
        )
        return themeRepository.save(theme)
    }
}
