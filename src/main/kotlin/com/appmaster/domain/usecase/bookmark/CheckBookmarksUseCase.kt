package com.appmaster.domain.usecase.bookmark

import com.appmaster.domain.model.valueobject.UserId
import com.appmaster.domain.repository.BookmarkRepository

class CheckBookmarksUseCase(
    private val bookmarkRepository: BookmarkRepository
) {
    data class Params(val userId: UserId, val bestItemIds: List<String>)

    suspend operator fun invoke(params: Params): Set<String> {
        if (params.bestItemIds.isEmpty()) return emptySet()
        return bookmarkRepository.findBookmarkedBestItemIds(params.userId, params.bestItemIds)
    }
}
