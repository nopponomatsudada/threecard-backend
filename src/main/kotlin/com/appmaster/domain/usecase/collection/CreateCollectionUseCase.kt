package com.appmaster.domain.usecase.collection

import com.appmaster.domain.error.DomainError
import com.appmaster.domain.error.DomainException
import com.appmaster.domain.model.entity.Collection
import com.appmaster.domain.model.`enum`.Plan
import com.appmaster.domain.model.valueobject.UserId
import com.appmaster.domain.repository.CollectionRepository
import com.appmaster.domain.repository.UserRepository

class CreateCollectionUseCase(
    private val collectionRepository: CollectionRepository,
    private val userRepository: UserRepository
) {
    companion object {
        const val FREE_COLLECTION_LIMIT = 3
    }

    data class Params(
        val userId: UserId,
        val title: String
    )

    suspend operator fun invoke(params: Params): Collection {
        if (params.title.isBlank()) {
            throw DomainException(DomainError.CollectionTitleRequired)
        }

        val user = userRepository.findById(params.userId)
            ?: throw DomainException(DomainError.NotFound("ユーザー"))

        if (user.plan == Plan.FREE) {
            val count = collectionRepository.countByUserId(params.userId)
            if (count >= FREE_COLLECTION_LIMIT) {
                throw DomainException(DomainError.CollectionLimitReached)
            }
        }

        val collection = Collection.create(userId = params.userId, title = params.title)
        return collectionRepository.save(collection)
    }
}
