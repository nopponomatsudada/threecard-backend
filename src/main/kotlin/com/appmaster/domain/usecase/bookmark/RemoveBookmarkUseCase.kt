package com.appmaster.domain.usecase.bookmark

import com.appmaster.domain.model.valueobject.BestId
import com.appmaster.domain.model.valueobject.UserId
import com.appmaster.domain.repository.BookmarkRepository

class RemoveBookmarkUseCase(
    private val bookmarkRepository: BookmarkRepository
) {
    data class Params(val userId: UserId, val bestId: BestId)

    suspend operator fun invoke(params: Params) {
        bookmarkRepository.deleteByUserIdAndBestId(params.userId, params.bestId)
    }
}
