@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.appmaster.data.repository

import com.appmaster.data.dao.UserDao
import com.appmaster.domain.model.entity.User
import com.appmaster.domain.model.valueobject.UserId
import com.appmaster.domain.repository.UserRepository
import kotlin.time.Clock

class UserRepositoryImpl(
    private val dao: UserDao,
    private val clock: Clock = Clock.System
) : UserRepository {

    override suspend fun findById(id: UserId): User? = dao.findById(id)

    override suspend fun findByDeviceId(deviceId: String): User? = dao.findByDeviceId(deviceId)

    override suspend fun save(user: User): User = dao.insert(user)

    override suspend fun updateDeviceSecretHash(user: User, hash: String): User {
        val now = clock.now()
        dao.updateDeviceSecretHash(user.id, hash, now)
        return user.copy(deviceSecretHash = hash, updatedAt = now)
    }
}
