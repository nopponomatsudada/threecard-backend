package com.appmaster.domain.usecase.user

import com.appmaster.domain.error.DomainError
import com.appmaster.domain.error.DomainException
import com.appmaster.domain.model.entity.ProfileWithStats
import com.appmaster.domain.model.valueobject.UserId
import com.appmaster.domain.repository.BestRepository
import com.appmaster.domain.repository.CollectionRepository
import com.appmaster.domain.repository.UserRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

class GetMyProfileUseCase(
    private val userRepository: UserRepository,
    private val bestRepository: BestRepository,
    private val collectionRepository: CollectionRepository
) {
    suspend operator fun invoke(userId: UserId): ProfileWithStats = coroutineScope {
        val user = userRepository.findById(userId)
            ?: throw DomainException(DomainError.Unauthorized)
        val bestCountAsync = async { bestRepository.countByAuthorId(userId) }
        val collectionCountAsync = async { collectionRepository.countByUserId(userId) }
        ProfileWithStats(user, bestCountAsync.await(), collectionCountAsync.await())
    }
}
