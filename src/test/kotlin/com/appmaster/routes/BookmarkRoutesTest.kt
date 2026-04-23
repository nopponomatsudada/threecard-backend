@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.appmaster.routes

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BookmarkRoutesTest {

    @BeforeTest fun setup() = setupTestDatabase()
    @AfterTest fun teardown() = tearDownTestDatabase()

    private suspend fun HttpClient.createBest(token: String, tagId: String = "music"): String {
        val themeResp = post("/api/v1/themes") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"title":"Theme for $tagId","tagId":"$tagId"}""")
        }
        val themeId = Json.parseToJsonElement(themeResp.bodyAsText())
            .jsonObject["data"]!!.jsonObject["id"]!!.jsonPrimitive.content

        val bestResp = post("/api/v1/themes/$themeId/bests") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"items":[{"rank":1,"name":"Item 1"},{"rank":2,"name":"Item 2"},{"rank":3,"name":"Item 3"}]}""")
        }
        approveAllContent()
        return Json.parseToJsonElement(bestResp.bodyAsText())
            .jsonObject["data"]!!.jsonObject["id"]!!.jsonPrimitive.content
    }

    @Test
    fun `POST bookmarks creates bookmark and returns 201`() = testApplication {
        configureFullTestApp()
        val client = jsonClient()
        val authorToken = client.getToken("device-bm-001a")
        val bestId = client.createBest(authorToken)

        val viewerToken = client.getToken("device-bm-001b")
        val response = client.post("/api/v1/bookmarks") {
            header(HttpHeaders.Authorization, "Bearer $viewerToken")
            contentType(ContentType.Application.Json)
            setBody("""{"bestId":"$bestId"}""")
        }

        assertEquals(HttpStatusCode.Created, response.status)
        val data = Json.parseToJsonElement(response.bodyAsText()).jsonObject["data"]!!.jsonObject
        assertEquals(bestId, data["bestId"]!!.jsonPrimitive.content)
        assertTrue(data.containsKey("createdAt"))
    }

    @Test
    fun `POST bookmarks duplicate returns 409`() = testApplication {
        configureFullTestApp()
        val client = jsonClient()
        val authorToken = client.getToken("device-bm-002a")
        val bestId = client.createBest(authorToken)

        val viewerToken = client.getToken("device-bm-002b")
        client.post("/api/v1/bookmarks") {
            header(HttpHeaders.Authorization, "Bearer $viewerToken")
            contentType(ContentType.Application.Json)
            setBody("""{"bestId":"$bestId"}""")
        }

        val response = client.post("/api/v1/bookmarks") {
            header(HttpHeaders.Authorization, "Bearer $viewerToken")
            contentType(ContentType.Application.Json)
            setBody("""{"bestId":"$bestId"}""")
        }

        assertEquals(HttpStatusCode.Conflict, response.status)
        val code = Json.parseToJsonElement(response.bodyAsText())
            .jsonObject["error"]!!.jsonObject["code"]!!.jsonPrimitive.content
        assertEquals("DUPLICATE_BOOKMARK", code)
    }

    @Test
    fun `POST bookmarks with non-existent bestId returns 404`() = testApplication {
        configureFullTestApp()
        val client = jsonClient()
        val token = client.getToken("device-bm-003")

        val response = client.post("/api/v1/bookmarks") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"bestId":"00000000-0000-0000-0000-000000000000"}""")
        }

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `DELETE bookmarks removes bookmark and returns 204`() = testApplication {
        configureFullTestApp()
        val client = jsonClient()
        val authorToken = client.getToken("device-bm-004a")
        val bestId = client.createBest(authorToken)

        val viewerToken = client.getToken("device-bm-004b")
        client.post("/api/v1/bookmarks") {
            header(HttpHeaders.Authorization, "Bearer $viewerToken")
            contentType(ContentType.Application.Json)
            setBody("""{"bestId":"$bestId"}""")
        }

        val response = client.delete("/api/v1/bookmarks/$bestId") {
            header(HttpHeaders.Authorization, "Bearer $viewerToken")
        }

        assertEquals(HttpStatusCode.NoContent, response.status)

        // Verify bookmark is gone
        val listResp = client.get("/api/v1/bookmarks") {
            header(HttpHeaders.Authorization, "Bearer $viewerToken")
        }
        val data = Json.parseToJsonElement(listResp.bodyAsText()).jsonObject["data"]!!.jsonArray
        assertEquals(0, data.size)
    }

    @Test
    fun `DELETE bookmarks on non-existent bookmark returns 204 (idempotent)`() = testApplication {
        configureFullTestApp()
        val client = jsonClient()
        val token = client.getToken("device-bm-005")

        val response = client.delete("/api/v1/bookmarks/00000000-0000-0000-0000-000000000000") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.NoContent, response.status)
    }

    @Test
    fun `GET bookmarks returns bookmarked cards with details`() = testApplication {
        configureFullTestApp()
        val client = jsonClient()
        val authorToken = client.getToken("device-bm-006a")
        val bestId = client.createBest(authorToken)

        val viewerToken = client.getToken("device-bm-006b")
        client.post("/api/v1/bookmarks") {
            header(HttpHeaders.Authorization, "Bearer $viewerToken")
            contentType(ContentType.Application.Json)
            setBody("""{"bestId":"$bestId"}""")
        }

        val response = client.get("/api/v1/bookmarks") {
            header(HttpHeaders.Authorization, "Bearer $viewerToken")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val data = Json.parseToJsonElement(response.bodyAsText()).jsonObject["data"]!!.jsonArray
        assertEquals(1, data.size)

        val card = data[0].jsonObject
        assertEquals(bestId, card["id"]!!.jsonPrimitive.content)
        assertTrue(card.containsKey("themeTitle"))
        assertTrue(card.containsKey("tagName"))
        assertTrue(card.containsKey("items"))
        assertEquals(true, card["isBookmarked"]!!.jsonPrimitive.content.toBoolean())
    }

    @Test
    fun `GET bookmarks with pagination works`() = testApplication {
        configureFullTestApp()
        val client = jsonClient()
        val authorToken = client.getToken("device-bm-007a")
        val bestId1 = client.createBest(authorToken, "music")

        val authorToken2 = client.getToken("device-bm-007a2")
        val bestId2 = client.createBest(authorToken2, "books")

        val viewerToken = client.getToken("device-bm-007b")
        client.post("/api/v1/bookmarks") {
            header(HttpHeaders.Authorization, "Bearer $viewerToken")
            contentType(ContentType.Application.Json)
            setBody("""{"bestId":"$bestId1"}""")
        }
        client.post("/api/v1/bookmarks") {
            header(HttpHeaders.Authorization, "Bearer $viewerToken")
            contentType(ContentType.Application.Json)
            setBody("""{"bestId":"$bestId2"}""")
        }

        val response = client.get("/api/v1/bookmarks?limit=1&offset=0") {
            header(HttpHeaders.Authorization, "Bearer $viewerToken")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val data = Json.parseToJsonElement(response.bodyAsText()).jsonObject["data"]!!.jsonArray
        assertEquals(1, data.size)
    }

    @Test
    fun `GET bookmarks check returns bookmarked IDs`() = testApplication {
        configureFullTestApp()
        val client = jsonClient()
        val authorToken = client.getToken("device-bm-008a")
        val bestId1 = client.createBest(authorToken, "music")

        val authorToken2 = client.getToken("device-bm-008a2")
        val bestId2 = client.createBest(authorToken2, "books")

        val viewerToken = client.getToken("device-bm-008b")
        client.post("/api/v1/bookmarks") {
            header(HttpHeaders.Authorization, "Bearer $viewerToken")
            contentType(ContentType.Application.Json)
            setBody("""{"bestId":"$bestId1"}""")
        }

        val response = client.get("/api/v1/bookmarks/check?bestIds=$bestId1,$bestId2") {
            header(HttpHeaders.Authorization, "Bearer $viewerToken")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val data = Json.parseToJsonElement(response.bodyAsText()).jsonObject["data"]!!.jsonArray
        val ids = data.map { it.jsonPrimitive.content }
        assertTrue(bestId1 in ids)
        assertTrue(bestId2 !in ids)
    }

    @Test
    fun `GET bookmarks check with empty bestIds returns empty`() = testApplication {
        configureFullTestApp()
        val client = jsonClient()
        val token = client.getToken("device-bm-009")

        val response = client.get("/api/v1/bookmarks/check?bestIds=") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val data = Json.parseToJsonElement(response.bodyAsText()).jsonObject["data"]!!.jsonArray
        assertEquals(0, data.size)
    }

    @Test
    fun `POST bookmarks without auth returns 401`() = testApplication {
        configureFullTestApp()

        val response = client.post("/api/v1/bookmarks") {
            contentType(ContentType.Application.Json)
            setBody("""{"bestId":"some-id"}""")
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `GET bookmarks with limit over 50 returns 400`() = testApplication {
        configureFullTestApp()
        val client = jsonClient()
        val token = client.getToken("device-bm-010")

        val response = client.get("/api/v1/bookmarks?limit=51") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }
}
