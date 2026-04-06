package com.appmaster.domain.usecase.auth

import com.appmaster.domain.error.DomainError
import com.appmaster.domain.error.DomainException
import com.appmaster.domain.model.entity.User
import com.appmaster.domain.repository.UserRepository

class DeviceAuthUseCase(
    private val userRepository: UserRepository
) {
    data class Result(
        val user: User,
        val isNewUser: Boolean
    )

    suspend operator fun invoke(deviceId: String): Result {
        if (deviceId.isBlank()) {
            throw DomainException(DomainError.ValidationError("deviceId は必須です"))
        }

        val existingUser = userRepository.findByDeviceId(deviceId)
        if (existingUser != null) {
            return Result(user = existingUser, isNewUser = false)
        }

        val newUser = User.create(deviceId)
        val savedUser = userRepository.save(newUser)
        return Result(user = savedUser, isNewUser = true)
    }
}
