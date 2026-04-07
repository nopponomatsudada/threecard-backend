@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.appmaster.routes

import com.appmaster.data.dao.BestDao
import com.appmaster.data.dao.CollectionDao
import com.appmaster.data.dao.DiscoverDao
import com.appmaster.data.dao.JwtBlocklistDao
import com.appmaster.data.dao.RefreshTokenDao
import com.appmaster.data.dao.ThemeDao
import com.appmaster.data.dao.UserDao
import com.appmaster.data.entity.BestItemsTable
import com.appmaster.data.entity.BestsTable
import com.appmaster.data.entity.CollectionCardsTable
import com.appmaster.data.entity.CollectionsTable
import com.appmaster.data.entity.JwtBlocklistTable
import com.appmaster.data.entity.RefreshTokensTable
import com.appmaster.data.entity.ThemesTable
import com.appmaster.data.entity.UsersTable
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
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.koin.core.module.Module
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

// ---------------------------------------------------------------------------
// Database lifecycle
// ---------------------------------------------------------------------------

internal val ALL_TABLES = arrayOf(
    UsersTable,
    ThemesTable,
    BestsTable,
    BestItemsTable,
    CollectionsTable,
    CollectionCardsTable,
    RefreshTokensTable,
    JwtBlocklistTable
)

internal fun setupTestDatabase() {
    Database.connect("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1", driver = "org.h2.Driver")
    transaction { SchemaUtils.create(*ALL_TABLES) }
}

internal fun tearDownTestDatabase() {
    transaction { SchemaUtils.drop(*ALL_TABLES.reversedArray()) }
}

// ---------------------------------------------------------------------------
// Auth wiring (used by every test that needs JWT)
// ---------------------------------------------------------------------------

internal val testJwtConfig = JwtConfig(
    secret = "test-secret-12345678901234567890",
    issuer = "appmaster",
    audience = "appmaster-app",
    realm = "AppMaster",
    accessTokenTtl = 15.minutes,
    refreshTokenTtl = 7.days
)

internal fun authTestModule(): Module = module {
    single<JwtConfig> { testJwtConfig }
    // BCrypt cost 4 in tests — fast, still real bcrypt format
    single<PasswordHasher> { BcryptPasswordHasher(cost = 4) }
    single<TokenProvider> { JwtTokenProvider(get()) }
    single { RefreshTokenDao() }
    single<RefreshTokenRepository> { RefreshTokenRepositoryImpl(get()) }
    single { JwtBlocklistDao() }
    single<JwtBlocklistRepository> { JwtBlocklistRepositoryImpl(get()) }
    single { UserDao() }
    single<UserRepository> { UserRepositoryImpl(get()) }
    single { DeviceAuthUseCase(get(), get(), get(), get()) }
    single { RefreshTokenUseCase(get(), get(), get()) }
    single { LogoutUseCase(get(), get()) }
}

fun fullTestModule() = module {
    includes(authTestModule())
    single { GetMyProfileUseCase(get(), get(), get()) }
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
        // DI must be installed before Authentication: validate{} pulls
        // JwtBlocklistRepository from Koin.
        this@application.install(Koin) { modules(fullTestModule()) }
        configureSerialization()
        configureAuthentication()
        configureRateLimit()
        configureStatusPages()
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

// ---------------------------------------------------------------------------
// Auth helpers
// ---------------------------------------------------------------------------

/**
 * Bootstrap a brand-new device, returning the issued access token. Used by
 * the legacy per-route tests that just need *some* valid JWT.
 */
suspend fun HttpClient.getToken(deviceId: String): String {
    val response = post("/api/v1/auth/device") {
        contentType(ContentType.Application.Json)
        setBody("""{"deviceId":"$deviceId"}""")
    }
    return Json.parseToJsonElement(response.bodyAsText())
        .jsonObject["data"]!!.jsonObject["accessToken"]!!.jsonPrimitive.content
}

/**
 * Bootstrap a device and return the full auth payload (access + refresh + secret).
 */
data class BootstrapResult(
    val accessToken: String,
    val refreshToken: String,
    val deviceSecret: String,
    val userId: String
)

suspend fun HttpClient.bootstrap(deviceId: String): BootstrapResult {
    val response = post("/api/v1/auth/device") {
        contentType(ContentType.Application.Json)
        setBody("""{"deviceId":"$deviceId"}""")
    }
    val data = Json.parseToJsonElement(response.bodyAsText()).jsonObject["data"]!!.jsonObject
    return BootstrapResult(
        accessToken = data["accessToken"]!!.jsonPrimitive.content,
        refreshToken = data["refreshToken"]!!.jsonPrimitive.content,
        deviceSecret = data["deviceSecret"]!!.jsonPrimitive.content,
        userId = data["user"]!!.jsonObject["id"]!!.jsonPrimitive.content
    )
}
