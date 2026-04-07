@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.appmaster.routes

import com.appmaster.data.entity.UsersTable
import com.appmaster.domain.model.`enum`.Plan
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.time.Clock
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AuthRoutesTest {

    @BeforeTest fun setup() = setupTestDatabase()
    @AfterTest fun teardown() = tearDownTestDatabase()

    private fun parse(text: String) = Json.parseToJsonElement(text).jsonObject

    @Test
    fun `bootstrap on a fresh device returns 201 with deviceSecret`() = testApplication {
        configureFullTestApp()
        val client = jsonClient()

        val response = client.post("/api/v1/auth/device") {
            contentType(ContentType.Application.Json)
            setBody("""{"deviceId":"dev-bootstrap-001"}""")
        }

        assertEquals(HttpStatusCode.Created, response.status)
        val data = parse(response.bodyAsText())["data"]!!.jsonObject
        assertNotNull(data["accessToken"]?.jsonPrimitive?.content)
        assertNotNull(data["refreshToken"]?.jsonPrimitive?.content)
        assertNotNull(data["deviceSecret"]?.jsonPrimitive?.content)
        assertTrue(data["expiresIn"]!!.jsonPrimitive.content.toLong() > 0)
        val user = data["user"]!!.jsonObject
        assertTrue(user["displayId"]!!.jsonPrimitive.content.startsWith("u_"))
    }

    @Test
    fun `login with correct deviceSecret returns 200 and no new secret`() = testApplication {
        configureFullTestApp()
        val client = jsonClient()

        val first = client.post("/api/v1/auth/device") {
            contentType(ContentType.Application.Json)
            setBody("""{"deviceId":"dev-login-002"}""")
        }
        val firstData = parse(first.bodyAsText())["data"]!!.jsonObject
        val secret = firstData["deviceSecret"]!!.jsonPrimitive.content

        val second = client.post("/api/v1/auth/device") {
            contentType(ContentType.Application.Json)
            setBody("""{"deviceId":"dev-login-002","deviceSecret":"$secret"}""")
        }
        assertEquals(HttpStatusCode.OK, second.status)
        val secondData = parse(second.bodyAsText())["data"]!!.jsonObject
        // The bootstrap secret must NOT be re-issued on subsequent logins.
        assertTrue(secondData["deviceSecret"] is JsonNull || secondData["deviceSecret"] == null)
    }

    @Test
    fun `login with wrong deviceSecret returns 401`() = testApplication {
        configureFullTestApp()
        val client = jsonClient()

        client.post("/api/v1/auth/device") {
            contentType(ContentType.Application.Json)
            setBody("""{"deviceId":"dev-wrong-003"}""")
        }
        val response = client.post("/api/v1/auth/device") {
            contentType(ContentType.Application.Json)
            setBody("""{"deviceId":"dev-wrong-003","deviceSecret":"definitely-wrong"}""")
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
        val code = parse(response.bodyAsText())["error"]!!.jsonObject["code"]!!.jsonPrimitive.content
        assertEquals("INVALID_DEVICE_CREDENTIALS", code)
    }

    @Test
    fun `login on existing device without deviceSecret returns 401`() = testApplication {
        configureFullTestApp()
        val client = jsonClient()

        client.post("/api/v1/auth/device") {
            contentType(ContentType.Application.Json)
            setBody("""{"deviceId":"dev-missing-004"}""")
        }
        val response = client.post("/api/v1/auth/device") {
            contentType(ContentType.Application.Json)
            setBody("""{"deviceId":"dev-missing-004"}""")
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `pre-rollout user with null hash gets silently re-bootstrapped`() = testApplication {
        configureFullTestApp()
        val client = jsonClient()

        // Simulate a legacy user: insert a row with no device_secret_hash.
        transaction {
            UsersTable.insert {
                it[id] = "legacy-user-id"
                it[deviceId] = "dev-legacy-099"
                it[deviceSecretHash] = null
                it[displayId] = "u_legac"
                it[plan] = Plan.FREE
                val now = Clock.System.now()
                it[createdAt] = now
                it[updatedAt] = now
            }
        }

        // Next /auth/device call must succeed (201) and return a fresh deviceSecret.
        val response = client.post("/api/v1/auth/device") {
            contentType(ContentType.Application.Json)
            setBody("""{"deviceId":"dev-legacy-099"}""")
        }
        assertEquals(HttpStatusCode.Created, response.status)
        val data = parse(response.bodyAsText())["data"]!!.jsonObject
        assertNotNull(data["deviceSecret"]?.jsonPrimitive?.content)
        assertNotNull(data["accessToken"]?.jsonPrimitive?.content)
        assertNotNull(data["refreshToken"]?.jsonPrimitive?.content)
        assertEquals("legacy-user-id", data["user"]!!.jsonObject["id"]!!.jsonPrimitive.content)

        // After re-bootstrap, the same user must require the new secret on subsequent calls.
        val newSecret = data["deviceSecret"]!!.jsonPrimitive.content
        val without = client.post("/api/v1/auth/device") {
            contentType(ContentType.Application.Json)
            setBody("""{"deviceId":"dev-legacy-099"}""")
        }
        assertEquals(HttpStatusCode.Unauthorized, without.status)
        val with = client.post("/api/v1/auth/device") {
            contentType(ContentType.Application.Json)
            setBody("""{"deviceId":"dev-legacy-099","deviceSecret":"$newSecret"}""")
        }
        assertEquals(HttpStatusCode.OK, with.status)
    }

    @Test
    fun `empty deviceId returns 400`() = testApplication {
        configureFullTestApp()
        val client = jsonClient()
        val response = client.post("/api/v1/auth/device") {
            contentType(ContentType.Application.Json)
            setBody("""{"deviceId":""}""")
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `refresh rotates the refresh token`() = testApplication {
        configureFullTestApp()
        val client = jsonClient()
        val boot = client.bootstrap("dev-refresh-005")

        val refreshResp = client.post("/api/v1/auth/refresh") {
            contentType(ContentType.Application.Json)
            setBody("""{"refreshToken":"${boot.refreshToken}"}""")
        }
        assertEquals(HttpStatusCode.OK, refreshResp.status)
        val data = parse(refreshResp.bodyAsText())["data"]!!.jsonObject
        val newRefresh = data["refreshToken"]!!.jsonPrimitive.content
        assertTrue(newRefresh != boot.refreshToken)

        // Old refresh token must now be revoked: re-using it returns 401.
        val reuseResp = client.post("/api/v1/auth/refresh") {
            contentType(ContentType.Application.Json)
            setBody("""{"refreshToken":"${boot.refreshToken}"}""")
        }
        assertEquals(HttpStatusCode.Unauthorized, reuseResp.status)
        assertEquals(
            "INVALID_REFRESH_TOKEN",
            parse(reuseResp.bodyAsText())["error"]!!.jsonObject["code"]!!.jsonPrimitive.content
        )

        // After reuse-detection, the *new* refresh token is also burned.
        val followupResp = client.post("/api/v1/auth/refresh") {
            contentType(ContentType.Application.Json)
            setBody("""{"refreshToken":"$newRefresh"}""")
        }
        assertEquals(HttpStatusCode.Unauthorized, followupResp.status)
    }

    @Test
    fun `logout blocklists the access JWT`() = testApplication {
        configureFullTestApp()
        val client = jsonClient()
        val boot = client.bootstrap("dev-logout-006")

        // Sanity: token works before logout.
        val before = client.get("/api/v1/users/me") {
            header(HttpHeaders.Authorization, "Bearer ${boot.accessToken}")
        }
        assertEquals(HttpStatusCode.OK, before.status)

        val logout = client.post("/api/v1/auth/logout") {
            header(HttpHeaders.Authorization, "Bearer ${boot.accessToken}")
        }
        assertEquals(HttpStatusCode.NoContent, logout.status)

        // Same JWT must now be rejected.
        val after = client.get("/api/v1/users/me") {
            header(HttpHeaders.Authorization, "Bearer ${boot.accessToken}")
        }
        assertEquals(HttpStatusCode.Unauthorized, after.status)
    }

    @Test
    fun `tags endpoint requires authentication`() = testApplication {
        configureFullTestApp()
        val client = jsonClient()
        val response = client.get("/api/v1/tags")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `tags endpoint succeeds with valid JWT`() = testApplication {
        configureFullTestApp()
        val client = jsonClient()
        val boot = client.bootstrap("dev-tags-007")
        val response = client.get("/api/v1/tags") {
            header(HttpHeaders.Authorization, "Bearer ${boot.accessToken}")
        }
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `users me with valid JWT returns profile`() = testApplication {
        configureFullTestApp()
        val client = jsonClient()
        val boot = client.bootstrap("dev-profile-008")
        val response = client.get("/api/v1/users/me") {
            header(HttpHeaders.Authorization, "Bearer ${boot.accessToken}")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val data = parse(response.bodyAsText())["data"]!!.jsonObject
        assertEquals("free", data["plan"]!!.jsonPrimitive.content)
        assertEquals(0, data["bestCount"]!!.jsonPrimitive.content.toInt())
    }

    @Test
    fun `users me without JWT returns 401`() = testApplication {
        configureFullTestApp()
        val response = client.get("/api/v1/users/me")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }
}
