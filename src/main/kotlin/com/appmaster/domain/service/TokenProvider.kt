package com.appmaster.domain.service

import com.appmaster.domain.model.entity.User

interface TokenProvider {
    fun generateAccessToken(user: User): String

    companion object {
        const val CLAIM_USER_ID = "userId"
    }
}
