package com.appmaster.domain.usecase.bookmark

import com.appmaster.domain.model.valueobject.UserId
import com.appmaster.domain.repository.BookmarkRepository

class RemoveBookmarkUseCase(
    private val bookmarkRepository: BookmarkRepository
) {
    data class Params(val userId: UserId, val bestItemId: String)

    suspend operator fun invoke(params: Params) {
        bookmarkRepository.deleteByUserIdAndBestItemId(params.userId, params.bestItemId)
    }
}
