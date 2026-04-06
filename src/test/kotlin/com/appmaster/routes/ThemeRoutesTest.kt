@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.appmaster.routes

import com.appmaster.data.dao.ThemeDao
import com.appmaster.data.dao.UserDao
import com.appmaster.data.entity.ThemesTable
import com.appmaster.data.entity.UsersTable
import com.appmaster.data.repository.ThemeRepositoryImpl
import com.appmaster.data.repository.UserRepositoryImpl
import com.appmaster.data.service.JwtTokenProvider
import com.appmaster.domain.repository.ThemeRepository
import com.appmaster.domain.repository.UserRepository
import com.appmaster.domain.service.TokenProvider
import com.appmaster.domain.usecase.auth.DeviceAuthUseCase
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
import kotlin.test.assertTrue

class ThemeRoutesTest {

    @BeforeTest
    fun setup() {
        Database.connect("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1", driver = "org.h2.Driver")
        transaction {
            SchemaUtils.create(UsersTable, ThemesTable)
        }
    }

    @AfterTest
    fun teardown() {
        transaction {
            SchemaUtils.drop(ThemesTable, UsersTable)
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
                })
            }
            routing {
                authRoutes()
                themeRoutes()
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

    @Test
    fun `POST themes creates theme and returns 201`() = testApplication {
        configureTestApp()
        val client = jsonClient()
        val token = client.getToken("device-theme-001")

        val response = client.post("/api/v1/themes") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"title":"My Theme","tagId":"music"}""")
        }

        assertEquals(HttpStatusCode.Created, response.status)
        val data = Json.parseToJsonElement(response.bodyAsText()).jsonObject["data"]!!.jsonObject
        assertEquals("My Theme", data["title"]!!.jsonPrimitive.content)
        assertEquals("music", data["tagId"]!!.jsonPrimitive.content)
        assertEquals(0, data["bestCount"]!!.jsonPrimitive.int)
    }

    @Test
    fun `POST themes with description creates theme`() = testApplication {
        configureTestApp()
        val client = jsonClient()
        val token = client.getToken("device-theme-002")

        val response = client.post("/api/v1/themes") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"title":"Theme with Desc","description":"テスト説明文","tagId":"books"}""")
        }

        assertEquals(HttpStatusCode.Created, response.status)
        val data = Json.parseToJsonElement(response.bodyAsText()).jsonObject["data"]!!.jsonObject
        assertEquals("テスト説明文", data["description"]!!.jsonPrimitive.content)
    }

    @Test
    fun `POST themes with invalid tagId returns 400`() = testApplication {
        configureTestApp()
        val client = jsonClient()
        val token = client.getToken("device-theme-003")

        val response = client.post("/api/v1/themes") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"title":"Bad Theme","tagId":"invalid"}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        val error = Json.parseToJsonElement(response.bodyAsText()).jsonObject["error"]!!.jsonObject
        assertEquals("TAG_NOT_SELECTED", error["code"]!!.jsonPrimitive.content)
    }

    @Test
    fun `POST themes with title over 100 chars returns 400`() = testApplication {
        configureTestApp()
        val client = jsonClient()
        val token = client.getToken("device-theme-004")
        val longTitle = "a".repeat(101)

        val response = client.post("/api/v1/themes") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"title":"$longTitle","tagId":"music"}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `POST themes with empty title returns 400`() = testApplication {
        configureTestApp()
        val client = jsonClient()
        val token = client.getToken("device-theme-005")

        val response = client.post("/api/v1/themes") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"title":"","tagId":"music"}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `POST themes without auth returns 401`() = testApplication {
        configureTestApp()

        val response = client.post("/api/v1/themes") {
            contentType(ContentType.Application.Json)
            setBody("""{"title":"Theme","tagId":"music"}""")
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `GET themes returns list`() = testApplication {
        configureTestApp()
        val client = jsonClient()
        val token = client.getToken("device-theme-006")

        client.post("/api/v1/themes") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"title":"Theme A","tagId":"books"}""")
        }

        val response = client.get("/api/v1/themes") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val data = Json.parseToJsonElement(response.bodyAsText()).jsonObject["data"]!!.jsonArray
        assertTrue(data.size >= 1)
    }

    @Test
    fun `GET themes with tagId filters correctly`() = testApplication {
        configureTestApp()
        val client = jsonClient()
        val token = client.getToken("device-theme-007")

        client.post("/api/v1/themes") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"title":"Music Theme","tagId":"music"}""")
        }
        client.post("/api/v1/themes") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"title":"Books Theme","tagId":"books"}""")
        }

        val response = client.get("/api/v1/themes?tagId=music") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        val data = Json.parseToJsonElement(response.bodyAsText()).jsonObject["data"]!!.jsonArray
        assertTrue(data.all { it.jsonObject["tagId"]!!.jsonPrimitive.content == "music" })
    }

    @Test
    fun `GET theme by id returns theme detail`() = testApplication {
        configureTestApp()
        val client = jsonClient()
        val token = client.getToken("device-theme-008")

        val createResponse = client.post("/api/v1/themes") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"title":"Detail Theme","tagId":"travel"}""")
        }
        val createdId = Json.parseToJsonElement(createResponse.bodyAsText())
            .jsonObject["data"]!!.jsonObject["id"]!!.jsonPrimitive.content

        val response = client.get("/api/v1/themes/$createdId") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val data = Json.parseToJsonElement(response.bodyAsText()).jsonObject["data"]!!.jsonObject
        assertEquals(createdId, data["id"]!!.jsonPrimitive.content)
        assertEquals("Detail Theme", data["title"]!!.jsonPrimitive.content)
    }

    @Test
    fun `GET theme with unknown id returns 404`() = testApplication {
        configureTestApp()
        val client = jsonClient()
        val token = client.getToken("device-theme-009")

        val response = client.get("/api/v1/themes/non-existent-id") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.NotFound, response.status)
    }
}
