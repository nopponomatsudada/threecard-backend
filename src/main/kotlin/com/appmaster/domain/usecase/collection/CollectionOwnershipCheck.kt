package com.appmaster.domain.usecase.collection

import com.appmaster.domain.error.DomainError
import com.appmaster.domain.error.DomainException
import com.appmaster.domain.model.entity.Collection
import com.appmaster.domain.model.valueobject.CollectionId
import com.appmaster.domain.model.valueobject.UserId
import com.appmaster.domain.repository.CollectionRepository

internal suspend fun CollectionRepository.findOwnedCollection(
    collectionId: CollectionId,
    userId: UserId
): Collection {
    val collection = findById(collectionId)
        ?: throw DomainException(DomainError.NotFound("コレクション"))
    if (collection.userId != userId) {
        throw DomainException(DomainError.NotFound("コレクション"))
    }
    return collection
}
