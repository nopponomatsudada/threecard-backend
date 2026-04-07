@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.appmaster.di

import com.appmaster.data.dao.BestDao
import com.appmaster.data.dao.CollectionDao
import com.appmaster.data.dao.DiscoverDao
import com.appmaster.data.dao.JwtBlocklistDao
import com.appmaster.data.dao.RefreshTokenDao
import com.appmaster.data.dao.ThemeDao
import com.appmaster.data.dao.UserDao
import com.appmaster.data.repository.BestRepositoryImpl
import com.appmaster.data.repository.CollectionRepositoryImpl
import com.appmaster.data.repository.DiscoverRepositoryImpl
import com.appmaster.data.repository.JwtBlocklistRepositoryImpl
import com.appmaster.data.repository.RefreshTokenRepositoryImpl
import com.appmaster.data.repository.ThemeRepositoryImpl
import com.appmaster.data.repository.UserRepositoryImpl
import com.appmaster.data.service.BcryptPasswordHasher
import com.appmaster.data.service.JwtTokenProvider
import com.appmaster.domain.repository.BestRepository
import com.appmaster.domain.repository.CollectionRepository
import com.appmaster.domain.repository.DiscoverRepository
import com.appmaster.domain.repository.JwtBlocklistRepository
import com.appmaster.domain.repository.RefreshTokenRepository
import com.appmaster.domain.repository.ThemeRepository
import com.appmaster.domain.repository.UserRepository
import com.appmaster.domain.service.JwtConfig
import com.appmaster.domain.service.PasswordHasher
import com.appmaster.domain.service.TokenProvider
import com.appmaster.domain.usecase.auth.DeviceAuthUseCase
import com.appmaster.domain.usecase.auth.LogoutUseCase
import com.appmaster.domain.usecase.auth.RefreshTokenUseCase
import com.appmaster.domain.usecase.best.GetBestsByThemeUseCase
import com.appmaster.domain.usecase.best.GetMyBestsUseCase
import com.appmaster.domain.usecase.best.PostBestUseCase
import com.appmaster.domain.usecase.collection.AddCardToCollectionUseCase
import com.appmaster.domain.usecase.collection.CreateCollectionUseCase
import com.appmaster.domain.usecase.collection.DeleteCollectionUseCase
import com.appmaster.domain.usecase.collection.GetCollectionCardsUseCase
import com.appmaster.domain.usecase.collection.GetCollectionsUseCase
import com.appmaster.domain.usecase.collection.RemoveCardFromCollectionUseCase
import com.appmaster.domain.usecase.discover.GetRandomCardsUseCase
import com.appmaster.domain.usecase.theme.CreateThemeUseCase
import com.appmaster.domain.usecase.theme.GetThemeDetailUseCase
import com.appmaster.domain.usecase.theme.GetThemesUseCase
import com.appmaster.domain.usecase.user.GetMyProfileUseCase
import com.appmaster.plugins.configValue
import io.ktor.server.application.*
import org.koin.dsl.module
import kotlin.time.Duration.Companion.milliseconds

fun appModule(environment: ApplicationEnvironment) = module {
    single { environment }

    // Single source of truth for JWT/refresh-token configuration. The dev-secret
    // guard lives here so it cannot be bypassed by reading the env var elsewhere.
    single<JwtConfig> {
        val env: ApplicationEnvironment = get()
        val secret = env.configValue("jwt.secret", "JWT_SECRET", JwtConfig.DEV_SECRET)
        // Application.developmentMode is a top-level property; ApplicationEnvironment
        // exposes it via developmentMode as well.
        val devMode = environment.config.propertyOrNull("ktor.development")?.getString()?.toBoolean() == true
        if (!devMode && secret == JwtConfig.DEV_SECRET) {
            throw IllegalStateException("JWT_SECRET must be set to a non-default value in production")
        }
        val accessMs = env.configValue(
            "jwt.accessTokenExpiration",
            "JWT_ACCESS_TOKEN_EXPIRATION",
            JwtConfig.DEFAULT_ACCESS_TTL.inWholeMilliseconds.toString()
        ).toLong()
        val refreshMs = env.configValue(
            "jwt.refreshTokenExpiration",
            "JWT_REFRESH_TOKEN_EXPIRATION",
            JwtConfig.DEFAULT_REFRESH_TTL.inWholeMilliseconds.toString()
        ).toLong()
        JwtConfig(
            secret = secret,
            issuer = env.configValue("jwt.issuer", "JWT_ISSUER", "appmaster"),
            audience = env.configValue("jwt.audience", "JWT_AUDIENCE", "appmaster-app"),
            realm = env.configValue("jwt.realm", "JWT_REALM", "AppMaster"),
            accessTokenTtl = accessMs.milliseconds,
            refreshTokenTtl = refreshMs.milliseconds
        )
    }

    single<PasswordHasher> { BcryptPasswordHasher() }
    single<TokenProvider> { JwtTokenProvider(get()) }

    // Auth persistence
    single { RefreshTokenDao() }
    single<RefreshTokenRepository> { RefreshTokenRepositoryImpl(get()) }
    single { JwtBlocklistDao() }
    single<JwtBlocklistRepository> { JwtBlocklistRepositoryImpl(get()) }

    // Users
    single { UserDao() }
    single<UserRepository> { UserRepositoryImpl(get()) } // Clock defaulted
    single { DeviceAuthUseCase(get(), get(), get(), get()) }
    single { RefreshTokenUseCase(get(), get(), get()) }
    single { LogoutUseCase(get(), get()) }
    single { GetMyProfileUseCase(get(), get(), get()) }

    // Themes
    single { ThemeDao() }
    single<ThemeRepository> { ThemeRepositoryImpl(get()) }
    single { GetThemesUseCase(get()) }
    single { CreateThemeUseCase(get()) }
    single { GetThemeDetailUseCase(get()) }

    // Bests
    single { BestDao() }
    single<BestRepository> { BestRepositoryImpl(get()) }
    single { PostBestUseCase(get(), get()) }
    single { GetBestsByThemeUseCase(get(), get()) }
    single { GetMyBestsUseCase(get()) }

    // Discover
    single { DiscoverDao() }
    single<DiscoverRepository> { DiscoverRepositoryImpl(get()) }
    single { GetRandomCardsUseCase(get()) }

    // Collections
    single { CollectionDao() }
    single<CollectionRepository> { CollectionRepositoryImpl(get()) }
    single { GetCollectionsUseCase(get()) }
    single { CreateCollectionUseCase(get(), get()) }
    single { DeleteCollectionUseCase(get()) }
    single { GetCollectionCardsUseCase(get()) }
    single { AddCardToCollectionUseCase(get(), get()) }
    single { RemoveCardFromCollectionUseCase(get()) }
}
