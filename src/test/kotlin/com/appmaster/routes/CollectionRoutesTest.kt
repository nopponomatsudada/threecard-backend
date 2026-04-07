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
import kotlinx.serialization.json.int
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

class CollectionRoutesTest {

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
                userRoutes()
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

    private suspend fun HttpClient.createCollection(token: String, title: String = "My Collection"): String {
        val response = post("/api/v1/collections") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"title":"$title"}""")
        }
        return Json.parseToJsonElement(response.bodyAsText())
            .jsonObject["data"]!!.jsonObject["id"]!!.jsonPrimitive.content
    }

    private suspend fun HttpClient.createBest(token: String, tagId: String = "music"): String {
        val themeResponse = post("/api/v1/themes") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"title":"Theme $tagId","tagId":"$tagId"}""")
        }
        val themeId = Json.parseToJsonElement(themeResponse.bodyAsText())
            .jsonObject["data"]!!.jsonObject["id"]!!.jsonPrimitive.content

        val bestResponse = post("/api/v1/themes/$themeId/bests") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"items":[{"rank":1,"name":"Item 1"}]}""")
        }
        return Json.parseToJsonElement(bestResponse.bodyAsText())
            .jsonObject["data"]!!.jsonObject["id"]!!.jsonPrimitive.content
    }

    @Test
    fun `POST collections creates collection and returns 201`() = testApplication {
        configureTestApp()
        val client = jsonClient()
        val token = client.getToken("device-col-001")

        val response = client.post("/api/v1/collections") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"title":"Favorites"}""")
        }

        assertEquals(HttpStatusCode.Created, response.status)
        val data = Json.parseToJsonElement(response.bodyAsText()).jsonObject["data"]!!.jsonObject
        assertEquals("Favorites", data["title"]!!.jsonPrimitive.content)
        assertEquals(0, data["cardCount"]!!.jsonPrimitive.int)
    }

    @Test
    fun `POST collections with empty title returns 400`() = testApplication {
        configureTestApp()
        val client = jsonClient()
        val token = client.getToken("device-col-002")

        val response = client.post("/api/v1/collections") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"title":""}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        val error = Json.parseToJsonElement(response.bodyAsText()).jsonObject["error"]!!.jsonObject
        assertEquals("COLLECTION_TITLE_REQUIRED", error["code"]!!.jsonPrimitive.content)
    }

    @Test
    fun `POST collections 4th collection on FREE plan returns 409`() = testApplication {
        configureTestApp()
        val client = jsonClient()
        val token = client.getToken("device-col-003")

        client.createCollection(token, "Col 1")
        client.createCollection(token, "Col 2")
        client.createCollection(token, "Col 3")

        val response = client.post("/api/v1/collections") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"title":"Col 4"}""")
        }

        assertEquals(HttpStatusCode.Conflict, response.status)
        val error = Json.parseToJsonElement(response.bodyAsText()).jsonObject["error"]!!.jsonObject
        assertEquals("COLLECTION_LIMIT_REACHED", error["code"]!!.jsonPrimitive.content)
    }

    @Test
    fun `GET collections returns list with card counts`() = testApplication {
        configureTestApp()
        val client = jsonClient()
        val token = client.getToken("device-col-004")
        client.createCollection(token, "My Faves")

        val response = client.get("/api/v1/collections") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val data = Json.parseToJsonElement(response.bodyAsText()).jsonObject["data"]!!.jsonArray
        assertEquals(1, data.size)
        assertEquals("My Faves", data[0].jsonObject["title"]!!.jsonPrimitive.content)
    }

    @Test
    fun `DELETE collections removes collection`() = testApplication {
        configureTestApp()
        val client = jsonClient()
        val token = client.getToken("device-col-005")
        val collectionId = client.createCollection(token)

        val response = client.delete("/api/v1/collections/$collectionId") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.NoContent, response.status)

        val listResponse = client.get("/api/v1/collections") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        val data = Json.parseToJsonElement(listResponse.bodyAsText()).jsonObject["data"]!!.jsonArray
        assertEquals(0, data.size)
    }

    @Test
    fun `DELETE collections by other user returns 404`() = testApplication {
        configureTestApp()
        val client = jsonClient()
        val token1 = client.getToken("device-col-006a")
        val token2 = client.getToken("device-col-006b")
        val collectionId = client.createCollection(token1)

        val response = client.delete("/api/v1/collections/$collectionId") {
            header(HttpHeaders.Authorization, "Bearer $token2")
        }

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `POST cards adds card to collection and returns 201`() = testApplication {
        configureTestApp()
        val client = jsonClient()
        val token = client.getToken("device-col-007")
        val collectionId = client.createCollection(token)
        val bestId = client.createBest(token)

        val response = client.post("/api/v1/collections/$collectionId/cards") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"bestId":"$bestId"}""")
        }

        assertEquals(HttpStatusCode.Created, response.status)
        val data = Json.parseToJsonElement(response.bodyAsText()).jsonObject["data"]!!.jsonObject
        assertEquals(bestId, data["bestId"]!!.jsonPrimitive.content)
        assertEquals(collectionId, data["collectionId"]!!.jsonPrimitive.content)
    }

    @Test
    fun `POST cards duplicate returns 409 DUPLICATE_BOOKMARK`() = testApplication {
        configureTestApp()
        val client = jsonClient()
        val token = client.getToken("device-col-008")
        val collectionId = client.createCollection(token)
        val bestId = client.createBest(token)

        client.post("/api/v1/collections/$collectionId/cards") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"bestId":"$bestId"}""")
        }

        val response = client.post("/api/v1/collections/$collectionId/cards") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"bestId":"$bestId"}""")
        }

        assertEquals(HttpStatusCode.Conflict, response.status)
        val error = Json.parseToJsonElement(response.bodyAsText()).jsonObject["error"]!!.jsonObject
        assertEquals("DUPLICATE_BOOKMARK", error["code"]!!.jsonPrimitive.content)
    }

    @Test
    fun `DELETE cards removes card from collection`() = testApplication {
        configureTestApp()
        val client = jsonClient()
        val token = client.getToken("device-col-009")
        val collectionId = client.createCollection(token)
        val bestId = client.createBest(token)

        client.post("/api/v1/collections/$collectionId/cards") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"bestId":"$bestId"}""")
        }

        val response = client.delete("/api/v1/collections/$collectionId/cards/$bestId") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.NoContent, response.status)
    }

    @Test
    fun `GET collection cards returns card list`() = testApplication {
        configureTestApp()
        val client = jsonClient()
        val token = client.getToken("device-col-010")
        val collectionId = client.createCollection(token)
        val bestId = client.createBest(token)

        client.post("/api/v1/collections/$collectionId/cards") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"bestId":"$bestId"}""")
        }

        val response = client.get("/api/v1/collections/$collectionId/cards") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val data = Json.parseToJsonElement(response.bodyAsText()).jsonObject["data"]!!.jsonArray
        assertEquals(1, data.size)
    }

    @Test
    fun `POST collections without auth returns 401`() = testApplication {
        configureTestApp()

        val response = client.post("/api/v1/collections") {
            contentType(ContentType.Application.Json)
            setBody("""{"title":"Test"}""")
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `GET users me bests returns own bests`() = testApplication {
        configureTestApp()
        val client = jsonClient()
        val token = client.getToken("device-col-011")
        client.createBest(token)

        val response = client.get("/api/v1/users/me/bests") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val data = Json.parseToJsonElement(response.bodyAsText()).jsonObject["data"]!!.jsonArray
        assertEquals(1, data.size)
    }
}
