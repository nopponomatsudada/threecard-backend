package com.appmaster.di

import com.appmaster.data.dao.UserDao
import com.appmaster.data.repository.UserRepositoryImpl
import com.appmaster.data.service.JwtTokenProvider
import com.appmaster.domain.repository.UserRepository
import com.appmaster.domain.service.TokenProvider
import com.appmaster.domain.usecase.auth.DeviceAuthUseCase
import com.appmaster.domain.usecase.user.GetMyProfileUseCase
import com.appmaster.plugins.configValue
import io.ktor.server.application.*
import org.koin.dsl.module

fun appModule(environment: ApplicationEnvironment) = module {
    single { environment }

    single<TokenProvider> {
        val env: ApplicationEnvironment = get()
        JwtTokenProvider(
            secret = env.configValue("jwt.secret", "JWT_SECRET", "dev-secret-change-in-production"),
            issuer = env.configValue("jwt.issuer", "JWT_ISSUER", "appmaster"),
            audience = env.configValue("jwt.audience", "JWT_AUDIENCE", "appmaster-app"),
            accessTokenExpirationMs = env.configValue("jwt.accessTokenExpiration", "JWT_ACCESS_TOKEN_EXPIRATION", "2592000000").toLong()
        )
    }

    single { UserDao() }
    single<UserRepository> { UserRepositoryImpl(get()) }
    single { DeviceAuthUseCase(get()) }
    single { GetMyProfileUseCase(get()) }
}
