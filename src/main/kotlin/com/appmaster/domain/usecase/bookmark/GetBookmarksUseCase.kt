package com.appmaster.domain.usecase.bookmark

import com.appmaster.domain.model.entity.DiscoverCard
import com.appmaster.domain.model.valueobject.UserId
import com.appmaster.domain.repository.BookmarkRepository

class GetBookmarksUseCase(
    private val bookmarkRepository: BookmarkRepository
) {
    data class Params(val userId: UserId, val limit: Int, val offset: Int)

    suspend operator fun invoke(params: Params): List<DiscoverCard> {
        return bookmarkRepository.findByUserId(params.userId, params.limit, params.offset)
    }
}
