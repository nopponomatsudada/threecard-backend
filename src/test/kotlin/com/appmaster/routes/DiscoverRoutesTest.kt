@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.appmaster.routes

import com.appmaster.data.dao.BestDao
import com.appmaster.data.dao.CollectionDao
import com.appmaster.data.dao.DiscoverDao
import com.appmaster.data.dao.ThemeDao
import com.appmaster.data.dao.UserDao
import com.appmaster.data.entity.BestItemsTable
import com.appmaster.data.entity.BestsTable
import com.appmaster.data.entity.CollectionCardsTable
import com.appmaster.data.entity.CollectionsTable
import com.appmaster.data.entity.ThemesTable
import com.appmaster.data.entity.UsersTable
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
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.server.application.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DiscoverRoutesTest {

    @BeforeTest
    fun setup() {
        Database.connect("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1", driver = "org.h2.Driver")
        transaction {
            SchemaUtils.create(UsersTable, ThemesTable, BestsTable, BestItemsTable, CollectionsTable, CollectionCardsTable)
        }
    }

    @AfterTest
    fun teardown() {
        transaction {
            SchemaUtils.drop(CollectionCardsTable, CollectionsTable, BestItemsTable, BestsTable, ThemesTable, UsersTable)
        }
    }

    private fun ApplicationTestBuilder.configureTestApp() {
        application {
            configureSerialization()
            configureAuthentication()
            configureRateLimit()
            configureStatusPages()
            this@application.install(Koin) {
                modules(module {
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
                })
            }
            routing {
                authRoutes()
                themeRoutes()
                bestRoutes()
                discoverRoutes()
                collectionRoutes()
            }
        }
    }

    private fun ApplicationTestBuilder.jsonClient() = createClient {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    private suspend fun HttpClient.getToken(deviceId: String): String {
        val response = post("/api/v1/auth/device") {
            contentType(ContentType.Application.Json)
            setBody("""{"deviceId":"$deviceId"}""")
        }
        return Json.parseToJsonElement(response.bodyAsText())
            .jsonObject["data"]!!.jsonObject["accessToken"]!!.jsonPrimitive.content
    }

    private suspend fun HttpClient.createThemeAndBest(token: String, tagId: String = "music"): String {
        val themeResponse = post("/api/v1/themes") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"title":"Theme for $tagId","tagId":"$tagId"}""")
        }
        val themeId = Json.parseToJsonElement(themeResponse.bodyAsText())
            .jsonObject["data"]!!.jsonObject["id"]!!.jsonPrimitive.content

        post("/api/v1/themes/$themeId/bests") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"items":[{"rank":1,"name":"Item 1"}]}""")
        }
        return themeId
    }

    @Test
    fun `GET discover returns cards`() = testApplication {
        configureTestApp()
        val client = jsonClient()
        val token = client.getToken("device-discover-001")
        client.createThemeAndBest(token)

        val response = client.get("/api/v1/discover") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val data = Json.parseToJsonElement(response.bodyAsText()).jsonObject["data"]!!.jsonArray
        assertTrue(data.size >= 1)
        val card = data[0].jsonObject
        assertTrue(card.containsKey("themeTitle"))
        assertTrue(card.containsKey("tagName"))
        assertTrue(card.containsKey("authorDisplayId"))
        assertTrue(card.containsKey("isBookmarked"))
        assertTrue(card.containsKey("items"))
    }

    @Test
    fun `GET discover with tagId filters`() = testApplication {
        configureTestApp()
        val client = jsonClient()
        val token = client.getToken("device-discover-002")
        client.createThemeAndBest(token, "music")

        val token2 = client.getToken("device-discover-002b")
        client.createThemeAndBest(token2, "books")

        val response = client.get("/api/v1/discover?tagId=music") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val data = Json.parseToJsonElement(response.bodyAsText()).jsonObject["data"]!!.jsonArray
        assertTrue(data.all { it.jsonObject["tagName"]!!.jsonPrimitive.content == "音楽" })
    }

    @Test
    fun `GET discover without auth returns 401`() = testApplication {
        configureTestApp()

        val response = client.get("/api/v1/discover")

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `GET discover with no cards returns empty array`() = testApplication {
        configureTestApp()
        val client = jsonClient()
        val token = client.getToken("device-discover-003")

        val response = client.get("/api/v1/discover") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val data = Json.parseToJsonElement(response.bodyAsText()).jsonObject["data"]!!.jsonArray
        assertEquals(0, data.size)
    }
}
