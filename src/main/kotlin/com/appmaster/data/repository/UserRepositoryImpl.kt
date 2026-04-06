package com.appmaster.data.repository

import com.appmaster.data.dao.UserDao
import com.appmaster.domain.model.entity.User
import com.appmaster.domain.model.valueobject.UserId
import com.appmaster.domain.repository.UserRepository

class UserRepositoryImpl(
    private val dao: UserDao
) : UserRepository {

    override suspend fun findById(id: UserId): User? = dao.findById(id)

    override suspend fun findByDeviceId(deviceId: String): User? = dao.findByDeviceId(deviceId)

    override suspend fun save(user: User): User = dao.insert(user)
}
