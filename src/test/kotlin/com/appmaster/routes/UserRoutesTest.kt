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
    fun `GET users me returns bestCount 0 and bookmarkCount 0 for new user`() = testApplication {
        configureFullTestApp()
        val client = jsonClient()
        val token = client.getToken("device-user-001")

        val response = client.get("/api/v1/users/me") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val data = Json.parseToJsonElement(response.bodyAsText()).jsonObject["data"]!!.jsonObject
        assertEquals(0, data["bestCount"]!!.jsonPrimitive.int)
        assertEquals(0, data["bookmarkCount"]!!.jsonPrimitive.int)
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
    fun `GET users me returns correct bookmarkCount after bookmarking`() = testApplication {
        configureFullTestApp()
        val client = jsonClient()
        val authorToken = client.getToken("device-user-003a")

        // Create theme + best (by another user)
        val themeResp = client.post("/api/v1/themes") {
            header(HttpHeaders.Authorization, "Bearer $authorToken")
            contentType(ContentType.Application.Json)
            setBody("""{"title":"Test Theme","tagId":"music"}""")
        }
        val themeId = Json.parseToJsonElement(themeResp.bodyAsText())
            .jsonObject["data"]!!.jsonObject["id"]!!.jsonPrimitive.content
        val bestResp = client.post("/api/v1/themes/$themeId/bests") {
            header(HttpHeaders.Authorization, "Bearer $authorToken")
            contentType(ContentType.Application.Json)
            setBody("""{"items":[{"rank":1,"name":"Item A"}]}""")
        }
        approveAllContent()
        val bestItemId = Json.parseToJsonElement(bestResp.bodyAsText())
            .jsonObject["data"]!!.jsonObject["items"]!!.jsonArray[0]
            .jsonObject["id"]!!.jsonPrimitive.content

        // Bookmark the best item as a different user
        val viewerToken = client.getToken("device-user-003b")
        client.post("/api/v1/bookmarks") {
            header(HttpHeaders.Authorization, "Bearer $viewerToken")
            contentType(ContentType.Application.Json)
            setBody("""{"bestItemId":"$bestItemId"}""")
        }

        // Check profile
        val response = client.get("/api/v1/users/me") {
            header(HttpHeaders.Authorization, "Bearer $viewerToken")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val data = Json.parseToJsonElement(response.bodyAsText()).jsonObject["data"]!!.jsonObject
        assertEquals(1, data["bookmarkCount"]!!.jsonPrimitive.int)
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

    @Test
    fun `GET users me bests returns empty array for user with no bests`() = testApplication {
        configureFullTestApp()
        val client = jsonClient()
        val token = client.getToken("device-user-005")

        val response = client.get("/api/v1/users/me/bests") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val data = Json.parseToJsonElement(response.bodyAsText()).jsonObject["data"]!!.jsonArray
        assertEquals(0, data.size)
    }

    @Test
    fun `GET users me bests with limit over 50 returns 400`() = testApplication {
        configureFullTestApp()
        val client = jsonClient()
        val token = client.getToken("device-user-006")

        val response = client.get("/api/v1/users/me/bests?limit=51") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        val code = Json.parseToJsonElement(response.bodyAsText())
            .jsonObject["error"]!!.jsonObject["code"]!!.jsonPrimitive.content
        assertEquals("VALIDATION_ERROR", code)
    }

    @Test
    fun `GET users me bests with negative offset returns 400`() = testApplication {
        configureFullTestApp()
        val client = jsonClient()
        val token = client.getToken("device-user-007")

        val response = client.get("/api/v1/users/me/bests?offset=-1") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }
}
