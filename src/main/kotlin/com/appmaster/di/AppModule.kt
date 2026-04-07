package com.appmaster.di

import com.appmaster.data.dao.BestDao
import com.appmaster.data.dao.CollectionDao
import com.appmaster.data.dao.DiscoverDao
import com.appmaster.data.dao.ThemeDao
import com.appmaster.data.dao.UserDao
import com.appmaster.data.repository.BestRepositoryImpl
import com.appmaster.data.repository.CollectionRepositoryImpl
import com.appmaster.data.repository.DiscoverRepositoryImpl
import com.appmaster.data.repository.ThemeRepositoryImpl
import com.appmaster.data.repository.UserRepositoryImpl
import com.appmaster.data.service.JwtTokenProvider
import com.appmaster.domain.repository.BestRepository
import com.appmaster.domain.repository.CollectionRepository
import com.appmaster.domain.repository.DiscoverRepository
import com.appmaster.domain.repository.ThemeRepository
import com.appmaster.domain.repository.UserRepository
import com.appmaster.domain.service.TokenProvider
import com.appmaster.domain.usecase.auth.DeviceAuthUseCase
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
