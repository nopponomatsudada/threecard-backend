@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.appmaster.routes

import com.appmaster.data.dao.BestDao
import com.appmaster.data.dao.ThemeDao
import com.appmaster.data.dao.UserDao
import com.appmaster.data.entity.BestItemsTable
import com.appmaster.data.entity.BestsTable
import com.appmaster.data.entity.ThemesTable
import com.appmaster.data.entity.UsersTable
import com.appmaster.data.repository.BestRepositoryImpl
import com.appmaster.data.repository.ThemeRepositoryImpl
import com.appmaster.data.repository.UserRepositoryImpl
import com.appmaster.data.service.JwtTokenProvider
import com.appmaster.domain.repository.BestRepository
import com.appmaster.domain.repository.ThemeRepository
import com.appmaster.domain.repository.UserRepository
import com.appmaster.domain.service.TokenProvider
import com.appmaster.domain.usecase.auth.DeviceAuthUseCase
import com.appmaster.domain.usecase.best.GetBestsByThemeUseCase
import com.appmaster.domain.usecase.best.PostBestUseCase
import com.appmaster.domain.usecase.theme.CreateThemeUseCase
import com.appmaster.domain.usecase.theme.GetThemeDetailUseCase
import com.appmaster.domain.usecase.theme.GetThemesUseCase
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

class BestRoutesTest {

    @BeforeTest
    fun setup() {
        Database.connect("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1", driver = "org.h2.Driver")
        transaction {
            SchemaUtils.create(UsersTable, ThemesTable, BestsTable, BestItemsTable)
        }
    }

    @AfterTest
    fun teardown() {
        transaction {
            SchemaUtils.drop(BestItemsTable, BestsTable, ThemesTable, UsersTable)
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
                    single { ThemeDao() }
                    single<ThemeRepository> { ThemeRepositoryImpl(get()) }
                    single { GetThemesUseCase(get()) }
                    single { CreateThemeUseCase(get()) }
                    single { GetThemeDetailUseCase(get()) }
                    single { BestDao() }
                    single<BestRepository> { BestRepositoryImpl(get()) }
                    single { PostBestUseCase(get(), get()) }
                    single { GetBestsByThemeUseCase(get(), get()) }
                })
            }
            routing {
                authRoutes()
                themeRoutes()
                bestRoutes()
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

    private suspend fun HttpClient.createTheme(token: String, title: String = "Test Theme", tagId: String = "music"): String {
        val response = post("/api/v1/themes") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"title":"$title","tagId":"$tagId"}""")
        }
        return Json.parseToJsonElement(response.bodyAsText())
            .jsonObject["data"]!!.jsonObject["id"]!!.jsonPrimitive.content
    }

    @Test
    fun `POST bests creates best and returns 201`() = testApplication {
        configureTestApp()
        val client = jsonClient()
        val token = client.getToken("device-best-001")
        val themeId = client.createTheme(token)

        val response = client.post("/api/v1/themes/$themeId/bests") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"items":[{"rank":1,"name":"Item 1","description":"Reason 1"},{"rank":2,"name":"Item 2"},{"rank":3,"name":"Item 3"}]}""")
        }

        assertEquals(HttpStatusCode.Created, response.status)
        val data = Json.parseToJsonElement(response.bodyAsText()).jsonObject["data"]!!.jsonObject
        assertEquals(themeId, data["themeId"]!!.jsonPrimitive.content)
        val items = data["items"]!!.jsonArray
        assertEquals(3, items.size)
        assertEquals("Item 1", items[0].jsonObject["name"]!!.jsonPrimitive.content)
        assertEquals("Reason 1", items[0].jsonObject["description"]!!.jsonPrimitive.content)
    }

    @Test
    fun `POST bests with single item returns 201`() = testApplication {
        configureTestApp()
        val client = jsonClient()
        val token = client.getToken("device-best-002")
        val themeId = client.createTheme(token)

        val response = client.post("/api/v1/themes/$themeId/bests") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"items":[{"rank":1,"name":"Only Item"}]}""")
        }

        assertEquals(HttpStatusCode.Created, response.status)
        val items = Json.parseToJsonElement(response.bodyAsText())
            .jsonObject["data"]!!.jsonObject["items"]!!.jsonArray
        assertEquals(1, items.size)
    }

    @Test
    fun `POST bests duplicate returns 409 ALREADY_POSTED`() = testApplication {
        configureTestApp()
        val client = jsonClient()
        val token = client.getToken("device-best-003")
        val themeId = client.createTheme(token)

        client.post("/api/v1/themes/$themeId/bests") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"items":[{"rank":1,"name":"Item 1"}]}""")
        }

        val response = client.post("/api/v1/themes/$themeId/bests") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"items":[{"rank":1,"name":"Item 2"}]}""")
        }

        assertEquals(HttpStatusCode.Conflict, response.status)
        val error = Json.parseToJsonElement(response.bodyAsText()).jsonObject["error"]!!.jsonObject
        assertEquals("ALREADY_POSTED", error["code"]!!.jsonPrimitive.content)
    }

    @Test
    fun `POST bests with empty name returns 400`() = testApplication {
        configureTestApp()
        val client = jsonClient()
        val token = client.getToken("device-best-004")
        val themeId = client.createTheme(token)

        val response = client.post("/api/v1/themes/$themeId/bests") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"items":[{"rank":1,"name":""}]}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        val error = Json.parseToJsonElement(response.bodyAsText()).jsonObject["error"]!!.jsonObject
        assertEquals("BEST_ITEM_NAME_REQUIRED", error["code"]!!.jsonPrimitive.content)
    }

    @Test
    fun `POST bests with 4 items returns 400`() = testApplication {
        configureTestApp()
        val client = jsonClient()
        val token = client.getToken("device-best-005")
        val themeId = client.createTheme(token)

        val response = client.post("/api/v1/themes/$themeId/bests") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"items":[{"rank":1,"name":"A"},{"rank":2,"name":"B"},{"rank":3,"name":"C"},{"rank":1,"name":"D"}]}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `POST bests with description over 140 chars returns 400`() = testApplication {
        configureTestApp()
        val client = jsonClient()
        val token = client.getToken("device-best-006")
        val themeId = client.createTheme(token)
        val longDesc = "a".repeat(141)

        val response = client.post("/api/v1/themes/$themeId/bests") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"items":[{"rank":1,"name":"Item","description":"$longDesc"}]}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        val error = Json.parseToJsonElement(response.bodyAsText()).jsonObject["error"]!!.jsonObject
        assertEquals("BEST_ITEM_DESCRIPTION_TOO_LONG", error["code"]!!.jsonPrimitive.content)
    }

    @Test
    fun `POST bests without auth returns 401`() = testApplication {
        configureTestApp()

        val response = client.post("/api/v1/themes/some-id/bests") {
            contentType(ContentType.Application.Json)
            setBody("""{"items":[{"rank":1,"name":"Item"}]}""")
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `POST bests to non-existent theme returns 404`() = testApplication {
        configureTestApp()
        val client = jsonClient()
        val token = client.getToken("device-best-007")

        val response = client.post("/api/v1/themes/non-existent-id/bests") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"items":[{"rank":1,"name":"Item"}]}""")
        }

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `GET bests returns list`() = testApplication {
        configureTestApp()
        val client = jsonClient()
        val token = client.getToken("device-best-008")
        val themeId = client.createTheme(token)

        client.post("/api/v1/themes/$themeId/bests") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"items":[{"rank":1,"name":"Item 1"},{"rank":2,"name":"Item 2"}]}""")
        }

        val response = client.get("/api/v1/themes/$themeId/bests") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val data = Json.parseToJsonElement(response.bodyAsText()).jsonObject["data"]!!.jsonArray
        assertEquals(1, data.size)
        val items = data[0].jsonObject["items"]!!.jsonArray
        assertEquals(2, items.size)
    }

    @Test
    fun `GET bests for non-existent theme returns 404`() = testApplication {
        configureTestApp()
        val client = jsonClient()
        val token = client.getToken("device-best-009")

        val response = client.get("/api/v1/themes/non-existent-id/bests") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `POST bests with duplicate ranks returns 400`() = testApplication {
        configureTestApp()
        val client = jsonClient()
        val token = client.getToken("device-best-010")
        val themeId = client.createTheme(token)

        val response = client.post("/api/v1/themes/$themeId/bests") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"items":[{"rank":1,"name":"A"},{"rank":1,"name":"B"}]}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `POST bests with invalid rank returns 400`() = testApplication {
        configureTestApp()
        val client = jsonClient()
        val token = client.getToken("device-best-011")
        val themeId = client.createTheme(token)

        val response = client.post("/api/v1/themes/$themeId/bests") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"items":[{"rank":5,"name":"Invalid Rank"}]}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }
}
