@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.appmaster.routes

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class UserRoutesTest {

    @BeforeTest fun setup() = setupTestDatabase()
    @AfterTest fun teardown() = tearDownTestDatabase()

    @Test
    fun `GET users me returns bestCount 0 and collectionCount 0 for new user`() = testApplication {
        configureFullTestApp()
        val client = jsonClient()
        val token = client.getToken("device-user-001")

        val response = client.get("/api/v1/users/me") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val data = Json.parseToJsonElement(response.bodyAsText()).jsonObject["data"]!!.jsonObject
        assertEquals(0, data["bestCount"]!!.jsonPrimitive.int)
        assertEquals(0, data["collectionCount"]!!.jsonPrimitive.int)
        assertEquals("free", data["plan"]!!.jsonPrimitive.content)
    }

    @Test
    fun `GET users me returns correct bestCount after posting`() = testApplication {
        configureFullTestApp()
        val client = jsonClient()
        val token = client.getToken("device-user-002")

        // Create a theme
        val themeResponse = client.post("/api/v1/themes") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"title":"Favorite Songs","tagId":"music"}""")
        }
        val themeId = Json.parseToJsonElement(themeResponse.bodyAsText())
            .jsonObject["data"]!!.jsonObject["id"]!!.jsonPrimitive.content

        // Post a best
        client.post("/api/v1/themes/$themeId/bests") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"items":[{"rank":1,"name":"Song A"},{"rank":2,"name":"Song B"},{"rank":3,"name":"Song C"}]}""")
        }

        // Check profile
        val response = client.get("/api/v1/users/me") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val data = Json.parseToJsonElement(response.bodyAsText()).jsonObject["data"]!!.jsonObject
        assertEquals(1, data["bestCount"]!!.jsonPrimitive.int)
    }

    @Test
    fun `GET users me returns correct collectionCount after creating collections`() = testApplication {
        configureFullTestApp()
        val client = jsonClient()
        val token = client.getToken("device-user-003")

        // Create two collections
        client.post("/api/v1/collections") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"title":"Collection 1"}""")
        }
        client.post("/api/v1/collections") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"title":"Collection 2"}""")
        }

        // Check profile
        val response = client.get("/api/v1/users/me") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val data = Json.parseToJsonElement(response.bodyAsText()).jsonObject["data"]!!.jsonObject
        assertEquals(2, data["collectionCount"]!!.jsonPrimitive.int)
    }

    @Test
    fun `GET users me bests returns user bests with theme metadata`() = testApplication {
        configureFullTestApp()
        val client = jsonClient()
        val token = client.getToken("device-user-004")

        // Create theme and post best
        val themeResponse = client.post("/api/v1/themes") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"title":"Best Movies","tagId":"movies"}""")
        }
        val themeId = Json.parseToJsonElement(themeResponse.bodyAsText())
            .jsonObject["data"]!!.jsonObject["id"]!!.jsonPrimitive.content

        client.post("/api/v1/themes/$themeId/bests") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"items":[{"rank":1,"name":"Movie A"}]}""")
        }

        // Get my bests
        val response = client.get("/api/v1/users/me/bests") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val data = Json.parseToJsonElement(response.bodyAsText()).jsonObject["data"]!!.jsonArray
        assertEquals(1, data.size)

        // Verify enriched fields: themeTitle and tagId
        val first = data[0].jsonObject
        assertEquals("Best Movies", first["themeTitle"]!!.jsonPrimitive.content)
        assertEquals("movies", first["tagId"]!!.jsonPrimitive.content)
        assertEquals(themeId, first["themeId"]!!.jsonPrimitive.content)
    }
}
