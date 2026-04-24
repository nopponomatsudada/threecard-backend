package com.appmaster.domain.usecase.bookmark

import com.appmaster.domain.error.DomainError
import com.appmaster.domain.error.DomainException
import com.appmaster.domain.model.entity.Bookmark
import com.appmaster.domain.model.valueobject.UserId
import com.appmaster.domain.repository.BestRepository
import com.appmaster.domain.repository.BookmarkRepository

class AddBookmarkUseCase(
    private val bookmarkRepository: BookmarkRepository,
    private val bestRepository: BestRepository
) {
    data class Params(val userId: UserId, val bestItemId: String)

    suspend operator fun invoke(params: Params): Bookmark {
        bestRepository.findBestItemById(params.bestItemId)
            ?: throw DomainException(DomainError.NotFound("アイテム"))

        bookmarkRepository.findByUserIdAndBestItemId(params.userId, params.bestItemId)?.let {
            throw DomainException(DomainError.DuplicateBookmark)
        }

        val bookmark = Bookmark.create(userId = params.userId, bestItemId = params.bestItemId)
        return bookmarkRepository.save(bookmark)
    }
}
