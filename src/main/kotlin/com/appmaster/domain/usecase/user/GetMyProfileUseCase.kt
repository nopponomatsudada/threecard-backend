package com.appmaster.domain.usecase.user

import com.appmaster.domain.error.DomainError
import com.appmaster.domain.error.DomainException
import com.appmaster.domain.model.entity.User
import com.appmaster.domain.model.valueobject.UserId
import com.appmaster.domain.repository.UserRepository

class GetMyProfileUseCase(
    private val userRepository: UserRepository
) {
    suspend operator fun invoke(userId: UserId): User {
        return userRepository.findById(userId)
            ?: throw DomainException(DomainError.Unauthorized)
    }
}
