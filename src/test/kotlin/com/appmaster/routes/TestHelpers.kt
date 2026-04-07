@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.appmaster.routes

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
import com.appmaster.plugins.configureAuthentication
import com.appmaster.plugins.configureRateLimit
import com.appmaster.plugins.configureSerialization
import com.appmaster.plugins.configureStatusPages
import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin

fun fullTestModule() = module {
    single { UserDao() }
    single<UserRepository> { UserRepositoryImpl(get()) }
    single<TokenProvider> {
        JwtTokenProvider(
            secret = "dev-secret-change-in-production",
            issuer = "appmaster",
            audience = "appmaster-app",
            accessTokenExpirationMs = 2592000000L
        )
    }
    single { DeviceAuthUseCase(get()) }
    single { GetMyProfileUseCase(get()) }
    single { ThemeDao() }
    single<ThemeRepository> { ThemeRepositoryImpl(get()) }
    single { GetThemesUseCase(get()) }
    single { CreateThemeUseCase(get()) }
    single { GetThemeDetailUseCase(get()) }
    single { BestDao() }
    single<BestRepository> { BestRepositoryImpl(get()) }
    single { PostBestUseCase(get(), get()) }
    single { GetBestsByThemeUseCase(get(), get()) }
    single { GetMyBestsUseCase(get()) }
    single { DiscoverDao() }
    single<DiscoverRepository> { DiscoverRepositoryImpl(get()) }
    single { GetRandomCardsUseCase(get()) }
    single { CollectionDao() }
    single<CollectionRepository> { CollectionRepositoryImpl(get()) }
    single { GetCollectionsUseCase(get()) }
    single { CreateCollectionUseCase(get(), get()) }
    single { DeleteCollectionUseCase(get()) }
    single { GetCollectionCardsUseCase(get()) }
    single { AddCardToCollectionUseCase(get(), get()) }
    single { RemoveCardFromCollectionUseCase(get()) }
}

fun ApplicationTestBuilder.configureFullTestApp() {
    application {
        configureSerialization()
        configureAuthentication()
        configureRateLimit()
        configureStatusPages()
        this@application.install(Koin) {
            modules(fullTestModule())
        }
        routing {
            authRoutes()
            userRoutes()
            tagRoutes()
            themeRoutes()
            bestRoutes()
            discoverRoutes()
            collectionRoutes()
        }
    }
}

fun ApplicationTestBuilder.jsonClient() = createClient {
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true })
    }
}

suspend fun HttpClient.getToken(deviceId: String): String {
    val response = post("/api/v1/auth/device") {
        contentType(ContentType.Application.Json)
        setBody("""{"deviceId":"$deviceId"}""")
    }
    return Json.parseToJsonElement(response.bodyAsText())
        .jsonObject["data"]!!.jsonObject["accessToken"]!!.jsonPrimitive.content
}
