package com.appmaster.domain.usecase.bookmark

import com.appmaster.domain.error.DomainError
import com.appmaster.domain.error.DomainException
import com.appmaster.domain.model.entity.Bookmark
import com.appmaster.domain.model.valueobject.BestId
import com.appmaster.domain.model.valueobject.UserId
import com.appmaster.domain.repository.BestRepository
import com.appmaster.domain.repository.BookmarkRepository

class AddBookmarkUseCase(
    private val bookmarkRepository: BookmarkRepository,
    private val bestRepository: BestRepository
) {
    data class Params(val userId: UserId, val bestId: BestId)

    suspend operator fun invoke(params: Params): Bookmark {
        bestRepository.findById(params.bestId)
            ?: throw DomainException(DomainError.NotFound("ベスト"))

        bookmarkRepository.findByUserIdAndBestId(params.userId, params.bestId)?.let {
            throw DomainException(DomainError.DuplicateBookmark)
        }

        val bookmark = Bookmark.create(userId = params.userId, bestId = params.bestId)
        return bookmarkRepository.save(bookmark)
    }
}
