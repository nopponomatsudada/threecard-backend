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

class DiscoverRoutesTest {

    @BeforeTest fun setup() = setupTestDatabase()
    @AfterTest fun teardown() = tearDownTestDatabase()

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
        approveAllContent()
        return themeId
    }

    @Test
    fun `GET discover returns cards`() = testApplication {
        configureFullTestApp()
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
        configureFullTestApp()
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
        configureFullTestApp()

        val response = client.get("/api/v1/discover")

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `GET discover isBookmarked is false when current user has not bookmarked`() = testApplication {
        configureFullTestApp()
        val client = jsonClient()
        val authorToken = client.getToken("device-discover-bm-a")
        client.createThemeAndBest(authorToken, "music")

        val viewerToken = client.getToken("device-discover-bm-b")
        val response = client.get("/api/v1/discover") {
            header(HttpHeaders.Authorization, "Bearer $viewerToken")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val data = Json.parseToJsonElement(response.bodyAsText()).jsonObject["data"]!!.jsonArray
        assertTrue(data.size >= 1)
        assertTrue(data.all { !it.jsonObject["isBookmarked"]!!.jsonPrimitive.content.toBoolean() })
    }

    @Test
    fun `GET discover isBookmarked is true when current user has bookmarked the best`() = testApplication {
        configureFullTestApp()
        val client = jsonClient()
        val authorToken = client.getToken("device-discover-bm-c")
        client.createThemeAndBest(authorToken, "music")

        val viewerToken = client.getToken("device-discover-bm-d")

        // Viewer fetches discover once to learn the bestId.
        val firstLook = client.get("/api/v1/discover") {
            header(HttpHeaders.Authorization, "Bearer $viewerToken")
        }
        val firstData = Json.parseToJsonElement(firstLook.bodyAsText()).jsonObject["data"]!!.jsonArray
        assertTrue(firstData.size >= 1)
        val bestId = firstData[0].jsonObject["id"]!!.jsonPrimitive.content

        // Viewer creates a collection and bookmarks that best.
        val collectionResp = client.post("/api/v1/collections") {
            header(HttpHeaders.Authorization, "Bearer $viewerToken")
            contentType(ContentType.Application.Json)
            setBody("""{"title":"Saved"}""")
        }
        val collectionId = Json.parseToJsonElement(collectionResp.bodyAsText())
            .jsonObject["data"]!!.jsonObject["id"]!!.jsonPrimitive.content

        client.post("/api/v1/collections/$collectionId/cards") {
            header(HttpHeaders.Authorization, "Bearer $viewerToken")
            contentType(ContentType.Application.Json)
            setBody("""{"bestId":"$bestId"}""")
        }

        // Viewer now sees isBookmarked=true on that best.
        val secondLook = client.get("/api/v1/discover") {
            header(HttpHeaders.Authorization, "Bearer $viewerToken")
        }
        val secondData = Json.parseToJsonElement(secondLook.bodyAsText()).jsonObject["data"]!!.jsonArray
        val matching = secondData.firstOrNull { it.jsonObject["id"]!!.jsonPrimitive.content == bestId }
        assertTrue(matching != null)
        assertEquals(true, matching!!.jsonObject["isBookmarked"]!!.jsonPrimitive.content.toBoolean())
    }

    @Test
    fun `GET discover with no cards returns empty array`() = testApplication {
        configureFullTestApp()
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
