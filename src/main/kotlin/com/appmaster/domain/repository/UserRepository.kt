package com.appmaster.domain.repository

import com.appmaster.domain.model.entity.User
import com.appmaster.domain.model.valueobject.UserId

interface UserRepository {
    suspend fun findById(id: UserId): User?
    suspend fun findByDeviceId(deviceId: String): User?
    suspend fun save(user: User): User
}
