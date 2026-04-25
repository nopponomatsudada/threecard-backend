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

    /**
     * Creates a theme + best and returns the first bestItem's ID.
     */
    private suspend fun HttpClient.createBestAndGetItemId(token: String, tagId: String = "music"): String {
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
        val items = Json.parseToJsonElement(bestResp.bodyAsText())
            .jsonObject["data"]!!.jsonObject["items"]!!.jsonArray
        return items[0].jsonObject["id"]!!.jsonPrimitive.content
    }

    private suspend fun HttpClient.createPendingBestAndGetItemId(token: String, tagId: String = "music"): String {
        val themeResp = post("/api/v1/themes") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"title":"Pending Theme for $tagId","tagId":"$tagId"}""")
        }
        val themeId = Json.parseToJsonElement(themeResp.bodyAsText())
            .jsonObject["data"]!!.jsonObject["id"]!!.jsonPrimitive.content

        val bestResp = post("/api/v1/themes/$themeId/bests") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"items":[{"rank":1,"name":"Pending Item"}]}""")
        }
        val items = Json.parseToJsonElement(bestResp.bodyAsText())
            .jsonObject["data"]!!.jsonObject["items"]!!.jsonArray
        return items[0].jsonObject["id"]!!.jsonPrimitive.content
    }

    @Test
    fun `POST bookmarks creates bookmark and returns 201`() = testApplication {
        configureFullTestApp()
        val client = jsonClient()
        val authorToken = client.getToken("device-bm-001a")
        val bestItemId = client.createBestAndGetItemId(authorToken)

        val viewerToken = client.getToken("device-bm-001b")
        val response = client.post("/api/v1/bookmarks") {
            header(HttpHeaders.Authorization, "Bearer $viewerToken")
            contentType(ContentType.Application.Json)
            setBody("""{"bestItemId":"$bestItemId"}""")
        }

        assertEquals(HttpStatusCode.Created, response.status)
        val data = Json.parseToJsonElement(response.bodyAsText()).jsonObject["data"]!!.jsonObject
        assertEquals(bestItemId, data["bestItemId"]!!.jsonPrimitive.content)
        assertTrue(data.containsKey("createdAt"))
    }

    @Test
    fun `POST bookmarks duplicate returns 409`() = testApplication {
        configureFullTestApp()
        val client = jsonClient()
        val authorToken = client.getToken("device-bm-002a")
        val bestItemId = client.createBestAndGetItemId(authorToken)

        val viewerToken = client.getToken("device-bm-002b")
        client.post("/api/v1/bookmarks") {
            header(HttpHeaders.Authorization, "Bearer $viewerToken")
            contentType(ContentType.Application.Json)
            setBody("""{"bestItemId":"$bestItemId"}""")
        }

        val response = client.post("/api/v1/bookmarks") {
            header(HttpHeaders.Authorization, "Bearer $viewerToken")
            contentType(ContentType.Application.Json)
            setBody("""{"bestItemId":"$bestItemId"}""")
        }

        assertEquals(HttpStatusCode.Conflict, response.status)
        val code = Json.parseToJsonElement(response.bodyAsText())
            .jsonObject["error"]!!.jsonObject["code"]!!.jsonPrimitive.content
        assertEquals("DUPLICATE_BOOKMARK", code)
    }

    @Test
    fun `POST bookmarks with non-existent bestItemId returns 404`() = testApplication {
        configureFullTestApp()
        val client = jsonClient()
        val token = client.getToken("device-bm-003")

        val response = client.post("/api/v1/bookmarks") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"bestItemId":"00000000-0000-0000-0000-000000000000"}""")
        }

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `POST bookmarks for pending best returns 404`() = testApplication {
        configureFullTestApp()
        val client = jsonClient()
        val authorToken = client.getToken("device-bm-pending-001a")
        val bestItemId = client.createPendingBestAndGetItemId(authorToken)

        val viewerToken = client.getToken("device-bm-pending-001b")
        val response = client.post("/api/v1/bookmarks") {
            header(HttpHeaders.Authorization, "Bearer $viewerToken")
            contentType(ContentType.Application.Json)
            setBody("""{"bestItemId":"$bestItemId"}""")
        }

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `DELETE bookmarks removes bookmark and returns 204`() = testApplication {
        configureFullTestApp()
        val client = jsonClient()
        val authorToken = client.getToken("device-bm-004a")
        val bestItemId = client.createBestAndGetItemId(authorToken)

        val viewerToken = client.getToken("device-bm-004b")
        client.post("/api/v1/bookmarks") {
            header(HttpHeaders.Authorization, "Bearer $viewerToken")
            contentType(ContentType.Application.Json)
            setBody("""{"bestItemId":"$bestItemId"}""")
        }

        val response = client.delete("/api/v1/bookmarks/$bestItemId") {
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
    fun `GET bookmarks returns bookmarked items with details`() = testApplication {
        configureFullTestApp()
        val client = jsonClient()
        val authorToken = client.getToken("device-bm-006a")
        val bestItemId = client.createBestAndGetItemId(authorToken)

        val viewerToken = client.getToken("device-bm-006b")
        client.post("/api/v1/bookmarks") {
            header(HttpHeaders.Authorization, "Bearer $viewerToken")
            contentType(ContentType.Application.Json)
            setBody("""{"bestItemId":"$bestItemId"}""")
        }

        val response = client.get("/api/v1/bookmarks") {
            header(HttpHeaders.Authorization, "Bearer $viewerToken")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val data = Json.parseToJsonElement(response.bodyAsText()).jsonObject["data"]!!.jsonArray
        assertEquals(1, data.size)

        val item = data[0].jsonObject
        assertEquals(bestItemId, item["id"]!!.jsonPrimitive.content)
        assertTrue(item.containsKey("bestId"))
        assertTrue(item.containsKey("themeId"))
        assertTrue(item["themeId"]!!.jsonPrimitive.content.isNotBlank())
        assertTrue(item.containsKey("themeTitle"))
        assertTrue(item.containsKey("tagName"))
        assertTrue(item.containsKey("rank"))
        assertTrue(item.containsKey("name"))
        assertTrue(item.containsKey("authorDisplayId"))
    }

    @Test
    fun `GET bookmarks with pagination works`() = testApplication {
        configureFullTestApp()
        val client = jsonClient()
        val authorToken = client.getToken("device-bm-007a")
        val bestItemId1 = client.createBestAndGetItemId(authorToken, "music")

        val authorToken2 = client.getToken("device-bm-007a2")
        val bestItemId2 = client.createBestAndGetItemId(authorToken2, "books")

        val viewerToken = client.getToken("device-bm-007b")
        client.post("/api/v1/bookmarks") {
            header(HttpHeaders.Authorization, "Bearer $viewerToken")
            contentType(ContentType.Application.Json)
            setBody("""{"bestItemId":"$bestItemId1"}""")
        }
        client.post("/api/v1/bookmarks") {
            header(HttpHeaders.Authorization, "Bearer $viewerToken")
            contentType(ContentType.Application.Json)
            setBody("""{"bestItemId":"$bestItemId2"}""")
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
        val bestItemId1 = client.createBestAndGetItemId(authorToken, "music")

        val authorToken2 = client.getToken("device-bm-008a2")
        val bestItemId2 = client.createBestAndGetItemId(authorToken2, "books")

        val viewerToken = client.getToken("device-bm-008b")
        client.post("/api/v1/bookmarks") {
            header(HttpHeaders.Authorization, "Bearer $viewerToken")
            contentType(ContentType.Application.Json)
            setBody("""{"bestItemId":"$bestItemId1"}""")
        }

        val response = client.get("/api/v1/bookmarks/check?bestItemIds=$bestItemId1,$bestItemId2") {
            header(HttpHeaders.Authorization, "Bearer $viewerToken")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val data = Json.parseToJsonElement(response.bodyAsText()).jsonObject["data"]!!.jsonArray
        val ids = data.map { it.jsonPrimitive.content }
        assertTrue(bestItemId1 in ids)
        assertTrue(bestItemId2 !in ids)
    }

    @Test
    fun `Bookmarks become invisible after moderation rejects the best`() = testApplication {
        configureFullTestApp()
        val client = jsonClient()
        val authorToken = client.getToken("device-bm-hidden-001a")
        val bestItemId = client.createBestAndGetItemId(authorToken)
        val viewerToken = client.getToken("device-bm-hidden-001b")

        client.post("/api/v1/bookmarks") {
            header(HttpHeaders.Authorization, "Bearer $viewerToken")
            contentType(ContentType.Application.Json)
            setBody("""{"bestItemId":"$bestItemId"}""")
        }

        val bestId = client.get("/api/v1/bookmarks") {
            header(HttpHeaders.Authorization, "Bearer $viewerToken")
        }.let { response ->
            Json.parseToJsonElement(response.bodyAsText()).jsonObject["data"]!!.jsonArray[0]
                .jsonObject["bestId"]!!.jsonPrimitive.content
        }

        client.patch("/api/v1/admin/moderation/bests/$bestId") {
            header(TEST_CF_HEADER, cfAccessHeader())
            contentType(ContentType.Application.Json)
            setBody("""{"status":"rejected"}""")
        }

        val listResponse = client.get("/api/v1/bookmarks") {
            header(HttpHeaders.Authorization, "Bearer $viewerToken")
        }
        assertEquals(HttpStatusCode.OK, listResponse.status)
        val listData = Json.parseToJsonElement(listResponse.bodyAsText()).jsonObject["data"]!!.jsonArray
        assertEquals(0, listData.size)

        val checkResponse = client.get("/api/v1/bookmarks/check?bestItemIds=$bestItemId") {
            header(HttpHeaders.Authorization, "Bearer $viewerToken")
        }
        assertEquals(HttpStatusCode.OK, checkResponse.status)
        val checkData = Json.parseToJsonElement(checkResponse.bodyAsText()).jsonObject["data"]!!.jsonArray
        assertEquals(0, checkData.size)

        val profileResponse = client.get("/api/v1/users/me") {
            header(HttpHeaders.Authorization, "Bearer $viewerToken")
        }
        assertEquals(HttpStatusCode.OK, profileResponse.status)
        val profile = Json.parseToJsonElement(profileResponse.bodyAsText()).jsonObject["data"]!!.jsonObject
        assertEquals(0, profile["bookmarkCount"]!!.jsonPrimitive.content.toInt())
    }

    @Test
    fun `GET bookmarks check with empty bestItemIds returns empty`() = testApplication {
        configureFullTestApp()
        val client = jsonClient()
        val token = client.getToken("device-bm-009")

        val response = client.get("/api/v1/bookmarks/check?bestItemIds=") {
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
            setBody("""{"bestItemId":"some-id"}""")
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
