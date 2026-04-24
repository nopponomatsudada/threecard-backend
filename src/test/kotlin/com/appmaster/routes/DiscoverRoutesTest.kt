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
    fun `GET discover returns cards with items containing isBookmarked`() = testApplication {
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
        assertTrue(card.containsKey("items"))
        // isBookmarked is now on each item, not on the card
        val items = card["items"]!!.jsonArray
        assertTrue(items.size >= 1)
        assertTrue(items[0].jsonObject.containsKey("isBookmarked"))
        assertTrue(items[0].jsonObject.containsKey("id"))
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
    fun `GET discover isBookmarked is false on items when user has not bookmarked`() = testApplication {
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
        data.forEach { card ->
            card.jsonObject["items"]!!.jsonArray.forEach { item ->
                assertEquals(false, item.jsonObject["isBookmarked"]!!.jsonPrimitive.content.toBoolean())
            }
        }
    }

    @Test
    fun `GET discover isBookmarked is true on bookmarked item`() = testApplication {
        configureFullTestApp()
        val client = jsonClient()
        val authorToken = client.getToken("device-discover-bm-c")
        client.createThemeAndBest(authorToken, "music")

        val viewerToken = client.getToken("device-discover-bm-d")

        // Viewer fetches discover to get bestItemId.
        val firstLook = client.get("/api/v1/discover") {
            header(HttpHeaders.Authorization, "Bearer $viewerToken")
        }
        val firstData = Json.parseToJsonElement(firstLook.bodyAsText()).jsonObject["data"]!!.jsonArray
        assertTrue(firstData.size >= 1)
        val bestItemId = firstData[0].jsonObject["items"]!!.jsonArray[0]
            .jsonObject["id"]!!.jsonPrimitive.content

        // Viewer bookmarks that item.
        client.post("/api/v1/bookmarks") {
            header(HttpHeaders.Authorization, "Bearer $viewerToken")
            contentType(ContentType.Application.Json)
            setBody("""{"bestItemId":"$bestItemId"}""")
        }

        // Viewer now sees isBookmarked=true on that item.
        val secondLook = client.get("/api/v1/discover") {
            header(HttpHeaders.Authorization, "Bearer $viewerToken")
        }
        val secondData = Json.parseToJsonElement(secondLook.bodyAsText()).jsonObject["data"]!!.jsonArray
        val matchingItem = secondData.flatMap { card ->
            card.jsonObject["items"]!!.jsonArray.map { it.jsonObject }
        }.firstOrNull { it["id"]!!.jsonPrimitive.content == bestItemId }
        assertTrue(matchingItem != null)
        assertEquals(true, matchingItem!!["isBookmarked"]!!.jsonPrimitive.content.toBoolean())
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
