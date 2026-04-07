@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.appmaster.routes

import com.appmaster.data.dao.BestDao
import com.appmaster.data.dao.CollectionDao
import com.appmaster.data.dao.UserDao
import com.appmaster.data.entity.BestItemsTable
import com.appmaster.data.entity.BestsTable
import com.appmaster.data.entity.CollectionsTable
import com.appmaster.data.entity.ThemesTable
import com.appmaster.data.entity.UsersTable
import com.appmaster.data.repository.BestRepositoryImpl
import com.appmaster.data.repository.CollectionRepositoryImpl
import com.appmaster.data.repository.UserRepositoryImpl
import com.appmaster.data.service.JwtTokenProvider
import com.appmaster.domain.repository.BestRepository
import com.appmaster.domain.repository.CollectionRepository
import com.appmaster.domain.repository.UserRepository
import com.appmaster.domain.service.TokenProvider
import com.appmaster.domain.usecase.auth.DeviceAuthUseCase
import com.appmaster.domain.usecase.user.GetMyProfileUseCase
import com.appmaster.plugins.configureAuthentication
import com.appmaster.plugins.configureRateLimit
import com.appmaster.plugins.configureSerialization
import com.appmaster.plugins.configureStatusPages
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.ktor.client.plugins.contentnegotiation.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import io.ktor.server.application.*
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AuthRoutesTest {

    @BeforeTest
    fun setup() {
        Database.connect("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1", driver = "org.h2.Driver")
        transaction {
            SchemaUtils.create(UsersTable, ThemesTable, BestsTable, BestItemsTable, CollectionsTable)
        }
    }

    @AfterTest
    fun teardown() {
        transaction {
            SchemaUtils.drop(CollectionsTable, BestItemsTable, BestsTable, ThemesTable, UsersTable)
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
                    single { BestDao() }
                    single<BestRepository> { BestRepositoryImpl(get()) }
                    single { CollectionDao() }
                    single<CollectionRepository> { CollectionRepositoryImpl(get()) }
                    single { DeviceAuthUseCase(get()) }
                    single { GetMyProfileUseCase(get(), get(), get()) }
                })
            }
            routing {
                authRoutes()
                userRoutes()
            }
        }
    }

    private fun ApplicationTestBuilder.jsonClient() = createClient {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    @Test
    fun `POST device auth creates new user and returns 201`() = testApplication {
        configureTestApp()
        val client = jsonClient()

        val response = client.post("/api/v1/auth/device") {
            contentType(ContentType.Application.Json)
            setBody("""{"deviceId":"test-device-001"}""")
        }

        assertEquals(HttpStatusCode.Created, response.status)
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        val data = body["data"]!!.jsonObject
        assertNotNull(data["accessToken"]?.jsonPrimitive?.content)
        val user = data["user"]!!.jsonObject
        assertTrue(user["displayId"]!!.jsonPrimitive.content.startsWith("u_"))
    }

    @Test
    fun `POST device auth with same deviceId returns 200`() = testApplication {
        configureTestApp()
        val client = jsonClient()

        // First call - creates user
        client.post("/api/v1/auth/device") {
            contentType(ContentType.Application.Json)
            setBody("""{"deviceId":"test-device-002"}""")
        }

        // Second call - returns existing user
        val response = client.post("/api/v1/auth/device") {
            contentType(ContentType.Application.Json)
            setBody("""{"deviceId":"test-device-002"}""")
        }

        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `POST device auth with empty deviceId returns 400`() = testApplication {
        configureTestApp()
        val client = jsonClient()

        val response = client.post("/api/v1/auth/device") {
            contentType(ContentType.Application.Json)
            setBody("""{"deviceId":""}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `GET users me with valid JWT returns profile`() = testApplication {
        configureTestApp()
        val client = jsonClient()

        // Get token via device auth
        val authResponse = client.post("/api/v1/auth/device") {
            contentType(ContentType.Application.Json)
            setBody("""{"deviceId":"test-device-003"}""")
        }
        val authBody = Json.parseToJsonElement(authResponse.bodyAsText()).jsonObject
        val token = authBody["data"]!!.jsonObject["accessToken"]!!.jsonPrimitive.content

        // Use token to get profile
        val response = client.get("/api/v1/users/me") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        val data = body["data"]!!.jsonObject
        assertEquals("free", data["plan"]!!.jsonPrimitive.content)
        assertEquals(0, data["bestCount"]!!.jsonPrimitive.content.toInt())
    }

    @Test
    fun `GET users me without JWT returns 401`() = testApplication {
        configureTestApp()

        val response = client.get("/api/v1/users/me")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }
}
