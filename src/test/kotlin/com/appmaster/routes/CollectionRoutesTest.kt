@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.appmaster.routes

import io.ktor.client.*
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

class CollectionRoutesTest {

    @BeforeTest fun setup() = setupTestDatabase()
    @AfterTest fun teardown() = tearDownTestDatabase()

    private suspend fun HttpClient.createCollection(token: String, title: String = "My Collection"): String {
        val response = post("/api/v1/collections") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"title":"$title"}""")
        }
        return Json.parseToJsonElement(response.bodyAsText())
            .jsonObject["data"]!!.jsonObject["id"]!!.jsonPrimitive.content
    }

    private suspend fun HttpClient.createBest(token: String, tagId: String = "music"): String {
        val themeResponse = post("/api/v1/themes") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"title":"Theme $tagId","tagId":"$tagId"}""")
        }
        val themeId = Json.parseToJsonElement(themeResponse.bodyAsText())
            .jsonObject["data"]!!.jsonObject["id"]!!.jsonPrimitive.content

        val bestResponse = post("/api/v1/themes/$themeId/bests") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"items":[{"rank":1,"name":"Item 1"}]}""")
        }
        return Json.parseToJsonElement(bestResponse.bodyAsText())
            .jsonObject["data"]!!.jsonObject["id"]!!.jsonPrimitive.content
    }

    @Test
    fun `POST collections creates collection and returns 201`() = testApplication {
        configureFullTestApp()
        val client = jsonClient()
        val token = client.getToken("device-col-001")

        val response = client.post("/api/v1/collections") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"title":"Favorites"}""")
        }

        assertEquals(HttpStatusCode.Created, response.status)
        val data = Json.parseToJsonElement(response.bodyAsText()).jsonObject["data"]!!.jsonObject
        assertEquals("Favorites", data["title"]!!.jsonPrimitive.content)
        assertEquals(0, data["cardCount"]!!.jsonPrimitive.int)
    }

    @Test
    fun `POST collections with empty title returns 400`() = testApplication {
        configureFullTestApp()
        val client = jsonClient()
        val token = client.getToken("device-col-002")

        val response = client.post("/api/v1/collections") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"title":""}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        val error = Json.parseToJsonElement(response.bodyAsText()).jsonObject["error"]!!.jsonObject
        assertEquals("COLLECTION_TITLE_REQUIRED", error["code"]!!.jsonPrimitive.content)
    }

    @Test
    fun `POST collections 4th collection on FREE plan returns 409`() = testApplication {
        configureFullTestApp()
        val client = jsonClient()
        val token = client.getToken("device-col-003")

        client.createCollection(token, "Col 1")
        client.createCollection(token, "Col 2")
        client.createCollection(token, "Col 3")

        val response = client.post("/api/v1/collections") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"title":"Col 4"}""")
        }

        assertEquals(HttpStatusCode.Conflict, response.status)
        val error = Json.parseToJsonElement(response.bodyAsText()).jsonObject["error"]!!.jsonObject
        assertEquals("COLLECTION_LIMIT_REACHED", error["code"]!!.jsonPrimitive.content)
    }

    @Test
    fun `GET collections returns list with card counts`() = testApplication {
        configureFullTestApp()
        val client = jsonClient()
        val token = client.getToken("device-col-004")
        client.createCollection(token, "My Faves")

        val response = client.get("/api/v1/collections") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val data = Json.parseToJsonElement(response.bodyAsText()).jsonObject["data"]!!.jsonArray
        assertEquals(1, data.size)
        assertEquals("My Faves", data[0].jsonObject["title"]!!.jsonPrimitive.content)
    }

    @Test
    fun `DELETE collections removes collection`() = testApplication {
        configureFullTestApp()
        val client = jsonClient()
        val token = client.getToken("device-col-005")
        val collectionId = client.createCollection(token)

        val response = client.delete("/api/v1/collections/$collectionId") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.NoContent, response.status)

        val listResponse = client.get("/api/v1/collections") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        val data = Json.parseToJsonElement(listResponse.bodyAsText()).jsonObject["data"]!!.jsonArray
        assertEquals(0, data.size)
    }

    @Test
    fun `DELETE collections by other user returns 404`() = testApplication {
        configureFullTestApp()
        val client = jsonClient()
        val token1 = client.getToken("device-col-006a")
        val token2 = client.getToken("device-col-006b")
        val collectionId = client.createCollection(token1)

        val response = client.delete("/api/v1/collections/$collectionId") {
            header(HttpHeaders.Authorization, "Bearer $token2")
        }

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `POST cards adds card to collection and returns 201`() = testApplication {
        configureFullTestApp()
        val client = jsonClient()
        val token = client.getToken("device-col-007")
        val collectionId = client.createCollection(token)
        val bestId = client.createBest(token)

        val response = client.post("/api/v1/collections/$collectionId/cards") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"bestId":"$bestId"}""")
        }

        assertEquals(HttpStatusCode.Created, response.status)
        val data = Json.parseToJsonElement(response.bodyAsText()).jsonObject["data"]!!.jsonObject
        assertEquals(bestId, data["bestId"]!!.jsonPrimitive.content)
        assertEquals(collectionId, data["collectionId"]!!.jsonPrimitive.content)
    }

    @Test
    fun `POST cards duplicate returns 409 DUPLICATE_BOOKMARK`() = testApplication {
        configureFullTestApp()
        val client = jsonClient()
        val token = client.getToken("device-col-008")
        val collectionId = client.createCollection(token)
        val bestId = client.createBest(token)

        client.post("/api/v1/collections/$collectionId/cards") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"bestId":"$bestId"}""")
        }

        val response = client.post("/api/v1/collections/$collectionId/cards") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"bestId":"$bestId"}""")
        }

        assertEquals(HttpStatusCode.Conflict, response.status)
        val error = Json.parseToJsonElement(response.bodyAsText()).jsonObject["error"]!!.jsonObject
        assertEquals("DUPLICATE_BOOKMARK", error["code"]!!.jsonPrimitive.content)
    }

    @Test
    fun `DELETE cards removes card from collection`() = testApplication {
        configureFullTestApp()
        val client = jsonClient()
        val token = client.getToken("device-col-009")
        val collectionId = client.createCollection(token)
        val bestId = client.createBest(token)

        client.post("/api/v1/collections/$collectionId/cards") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"bestId":"$bestId"}""")
        }

        val response = client.delete("/api/v1/collections/$collectionId/cards/$bestId") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.NoContent, response.status)
    }

    @Test
    fun `GET collection cards returns card list`() = testApplication {
        configureFullTestApp()
        val client = jsonClient()
        val token = client.getToken("device-col-010")
        val collectionId = client.createCollection(token)
        val bestId = client.createBest(token)

        client.post("/api/v1/collections/$collectionId/cards") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"bestId":"$bestId"}""")
        }

        val response = client.get("/api/v1/collections/$collectionId/cards") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val data = Json.parseToJsonElement(response.bodyAsText()).jsonObject["data"]!!.jsonArray
        assertEquals(1, data.size)
    }

    @Test
    fun `POST collections without auth returns 401`() = testApplication {
        configureFullTestApp()

        val response = client.post("/api/v1/collections") {
            contentType(ContentType.Application.Json)
            setBody("""{"title":"Test"}""")
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `POST cards to non-owned collection returns 404`() = testApplication {
        configureFullTestApp()
        val client = jsonClient()
        val token1 = client.getToken("device-col-012a")
        val token2 = client.getToken("device-col-012b")
        val collectionId = client.createCollection(token1)
        val bestId = client.createBest(token2)

        val response = client.post("/api/v1/collections/$collectionId/cards") {
            header(HttpHeaders.Authorization, "Bearer $token2")
            contentType(ContentType.Application.Json)
            setBody("""{"bestId":"$bestId"}""")
        }

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `DELETE cards from non-owned collection returns 404`() = testApplication {
        configureFullTestApp()
        val client = jsonClient()
        val token1 = client.getToken("device-col-013a")
        val token2 = client.getToken("device-col-013b")
        val collectionId = client.createCollection(token1)
        val bestId = client.createBest(token1)
        client.post("/api/v1/collections/$collectionId/cards") {
            header(HttpHeaders.Authorization, "Bearer $token1")
            contentType(ContentType.Application.Json)
            setBody("""{"bestId":"$bestId"}""")
        }

        val response = client.delete("/api/v1/collections/$collectionId/cards/$bestId") {
            header(HttpHeaders.Authorization, "Bearer $token2")
        }

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `POST cards with non-existent bestId returns 404`() = testApplication {
        configureFullTestApp()
        val client = jsonClient()
        val token = client.getToken("device-col-014")
        val collectionId = client.createCollection(token)

        val response = client.post("/api/v1/collections/$collectionId/cards") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"bestId":"00000000-0000-0000-0000-000000000000"}""")
        }

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `GET users me bests returns own bests`() = testApplication {
        configureFullTestApp()
        val client = jsonClient()
        val token = client.getToken("device-col-011")
        client.createBest(token)

        val response = client.get("/api/v1/users/me/bests") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val data = Json.parseToJsonElement(response.bodyAsText()).jsonObject["data"]!!.jsonArray
        assertEquals(1, data.size)
    }
}
