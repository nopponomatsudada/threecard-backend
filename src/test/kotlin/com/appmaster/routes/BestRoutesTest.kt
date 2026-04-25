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

class BestRoutesTest {

    @BeforeTest fun setup() = setupTestDatabase()
    @AfterTest fun teardown() = tearDownTestDatabase()

    private fun ApplicationTestBuilder.configureTestApp() = configureFullTestApp()

    private suspend fun HttpClient.createTheme(token: String, title: String = "Test Theme", tagId: String = "music"): String {
        val response = post("/api/v1/themes") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"title":"$title","tagId":"$tagId"}""")
        }
        return Json.parseToJsonElement(response.bodyAsText())
            .jsonObject["data"]!!.jsonObject["id"]!!.jsonPrimitive.content
    }

    @Test
    fun `POST bests creates best and returns 201`() = testApplication {
        configureTestApp()
        val client = jsonClient()
        val token = client.getToken("device-best-001")
        val themeId = client.createTheme(token)

        val response = client.post("/api/v1/themes/$themeId/bests") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"items":[{"rank":1,"name":"Item 1","description":"Reason 1"},{"rank":2,"name":"Item 2"},{"rank":3,"name":"Item 3"}]}""")
        }

        assertEquals(HttpStatusCode.Created, response.status)
        val data = Json.parseToJsonElement(response.bodyAsText()).jsonObject["data"]!!.jsonObject
        assertEquals(themeId, data["themeId"]!!.jsonPrimitive.content)
        val items = data["items"]!!.jsonArray
        assertEquals(3, items.size)
        assertEquals("Item 1", items[0].jsonObject["name"]!!.jsonPrimitive.content)
        assertEquals("Reason 1", items[0].jsonObject["description"]!!.jsonPrimitive.content)
    }

    @Test
    fun `POST bests with single item returns 201`() = testApplication {
        configureTestApp()
        val client = jsonClient()
        val token = client.getToken("device-best-002")
        val themeId = client.createTheme(token)

        val response = client.post("/api/v1/themes/$themeId/bests") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"items":[{"rank":1,"name":"Only Item"}]}""")
        }

        assertEquals(HttpStatusCode.Created, response.status)
        val items = Json.parseToJsonElement(response.bodyAsText())
            .jsonObject["data"]!!.jsonObject["items"]!!.jsonArray
        assertEquals(1, items.size)
    }

    @Test
    fun `POST bests duplicate returns 409 ALREADY_POSTED`() = testApplication {
        configureTestApp()
        val client = jsonClient()
        val token = client.getToken("device-best-003")
        val themeId = client.createTheme(token)

        client.post("/api/v1/themes/$themeId/bests") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"items":[{"rank":1,"name":"Item 1"}]}""")
        }

        val response = client.post("/api/v1/themes/$themeId/bests") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"items":[{"rank":1,"name":"Item 2"}]}""")
        }

        assertEquals(HttpStatusCode.Conflict, response.status)
        val error = Json.parseToJsonElement(response.bodyAsText()).jsonObject["error"]!!.jsonObject
        assertEquals("ALREADY_POSTED", error["code"]!!.jsonPrimitive.content)
    }

    @Test
    fun `POST bests with empty name returns 400`() = testApplication {
        configureTestApp()
        val client = jsonClient()
        val token = client.getToken("device-best-004")
        val themeId = client.createTheme(token)

        val response = client.post("/api/v1/themes/$themeId/bests") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"items":[{"rank":1,"name":""}]}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        val error = Json.parseToJsonElement(response.bodyAsText()).jsonObject["error"]!!.jsonObject
        assertEquals("BEST_ITEM_NAME_REQUIRED", error["code"]!!.jsonPrimitive.content)
    }

    @Test
    fun `POST bests with 4 items returns 400`() = testApplication {
        configureTestApp()
        val client = jsonClient()
        val token = client.getToken("device-best-005")
        val themeId = client.createTheme(token)

        val response = client.post("/api/v1/themes/$themeId/bests") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"items":[{"rank":1,"name":"A"},{"rank":2,"name":"B"},{"rank":3,"name":"C"},{"rank":1,"name":"D"}]}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `POST bests with description over 140 chars returns 400`() = testApplication {
        configureTestApp()
        val client = jsonClient()
        val token = client.getToken("device-best-006")
        val themeId = client.createTheme(token)
        val longDesc = "a".repeat(141)

        val response = client.post("/api/v1/themes/$themeId/bests") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"items":[{"rank":1,"name":"Item","description":"$longDesc"}]}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        val error = Json.parseToJsonElement(response.bodyAsText()).jsonObject["error"]!!.jsonObject
        assertEquals("BEST_ITEM_DESCRIPTION_TOO_LONG", error["code"]!!.jsonPrimitive.content)
    }

    @Test
    fun `POST bests without auth returns 401`() = testApplication {
        configureTestApp()

        val response = client.post("/api/v1/themes/some-id/bests") {
            contentType(ContentType.Application.Json)
            setBody("""{"items":[{"rank":1,"name":"Item"}]}""")
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `POST bests to non-existent theme returns 404`() = testApplication {
        configureTestApp()
        val client = jsonClient()
        val token = client.getToken("device-best-007")

        val response = client.post("/api/v1/themes/00000000-0000-0000-0000-000000000000/bests") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"items":[{"rank":1,"name":"Item"}]}""")
        }

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `GET bests returns list`() = testApplication {
        configureTestApp()
        val client = jsonClient()
        val token = client.getToken("device-best-008")
        val themeId = client.createTheme(token)

        client.post("/api/v1/themes/$themeId/bests") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"items":[{"rank":1,"name":"Item 1"},{"rank":2,"name":"Item 2"}]}""")
        }
        approveAllContent()

        val response = client.get("/api/v1/themes/$themeId/bests") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val data = Json.parseToJsonElement(response.bodyAsText()).jsonObject["data"]!!.jsonArray
        assertEquals(1, data.size)
        val items = data[0].jsonObject["items"]!!.jsonArray
        assertEquals(2, items.size)
    }

    @Test
    fun `GET bests for non-existent theme returns 404`() = testApplication {
        configureTestApp()
        val client = jsonClient()
        val token = client.getToken("device-best-009")

        val response = client.get("/api/v1/themes/00000000-0000-0000-0000-000000000000/bests") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `POST bests with duplicate ranks returns 400`() = testApplication {
        configureTestApp()
        val client = jsonClient()
        val token = client.getToken("device-best-010")
        val themeId = client.createTheme(token)

        val response = client.post("/api/v1/themes/$themeId/bests") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"items":[{"rank":1,"name":"A"},{"rank":1,"name":"B"}]}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `POST bests with empty items returns 400`() = testApplication {
        configureTestApp()
        val client = jsonClient()
        val token = client.getToken("device-best-012")
        val themeId = client.createTheme(token)

        val response = client.post("/api/v1/themes/$themeId/bests") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"items":[]}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `POST bests with invalid rank returns 400`() = testApplication {
        configureTestApp()
        val client = jsonClient()
        val token = client.getToken("device-best-011")
        val themeId = client.createTheme(token)

        val response = client.post("/api/v1/themes/$themeId/bests") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"items":[{"rank":5,"name":"Invalid Rank"}]}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    private suspend fun HttpClient.postBest(
        token: String,
        themeId: String,
        body: String = """{"items":[{"rank":1,"name":"Item 1"},{"rank":2,"name":"Item 2"},{"rank":3,"name":"Item 3"}]}""",
    ): String {
        val response = post("/api/v1/themes/$themeId/bests") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        return Json.parseToJsonElement(response.bodyAsText())
            .jsonObject["data"]!!.jsonObject["id"]!!.jsonPrimitive.content
    }

    @Test
    fun `PUT bests updates own best and returns 200 with PENDING moderation`() = testApplication {
        configureTestApp()
        val client = jsonClient()
        val token = client.getToken("device-best-put-001")
        val themeId = client.createTheme(token)
        val bestId = client.postBest(token, themeId)

        val response = client.put("/api/v1/themes/$themeId/bests/$bestId") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"items":[{"rank":1,"name":"Updated 1","description":"new"},{"rank":2,"name":"Updated 2"}]}""")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val data = Json.parseToJsonElement(response.bodyAsText()).jsonObject["data"]!!.jsonObject
        assertEquals(bestId, data["id"]!!.jsonPrimitive.content)
        assertEquals("pending", data["moderationStatus"]!!.jsonPrimitive.content)
        val items = data["items"]!!.jsonArray
        assertEquals(2, items.size)
        assertEquals("Updated 1", items[0].jsonObject["name"]!!.jsonPrimitive.content)
        assertEquals("new", items[0].jsonObject["description"]!!.jsonPrimitive.content)
    }

    @Test
    fun `PUT bests by another user returns 403 FORBIDDEN`() = testApplication {
        configureTestApp()
        val client = jsonClient()
        val ownerToken = client.getToken("device-best-put-002-owner")
        val themeId = client.createTheme(ownerToken)
        val bestId = client.postBest(ownerToken, themeId)

        val attackerToken = client.getToken("device-best-put-002-attacker")
        val response = client.put("/api/v1/themes/$themeId/bests/$bestId") {
            header(HttpHeaders.Authorization, "Bearer $attackerToken")
            contentType(ContentType.Application.Json)
            setBody("""{"items":[{"rank":1,"name":"Hijack"}]}""")
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
        val error = Json.parseToJsonElement(response.bodyAsText()).jsonObject["error"]!!.jsonObject
        assertEquals("FORBIDDEN", error["code"]!!.jsonPrimitive.content)
    }

    @Test
    fun `PUT bests non-existent bestId returns 404`() = testApplication {
        configureTestApp()
        val client = jsonClient()
        val token = client.getToken("device-best-put-003")
        val themeId = client.createTheme(token)

        val response = client.put("/api/v1/themes/$themeId/bests/00000000-0000-0000-0000-000000000000") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"items":[{"rank":1,"name":"x"}]}""")
        }

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `PUT bests with empty name returns 400`() = testApplication {
        configureTestApp()
        val client = jsonClient()
        val token = client.getToken("device-best-put-004")
        val themeId = client.createTheme(token)
        val bestId = client.postBest(token, themeId)

        val response = client.put("/api/v1/themes/$themeId/bests/$bestId") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"items":[{"rank":1,"name":"  "}]}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `PUT bests with duplicate ranks returns 400`() = testApplication {
        configureTestApp()
        val client = jsonClient()
        val token = client.getToken("device-best-put-005")
        val themeId = client.createTheme(token)
        val bestId = client.postBest(token, themeId)

        val response = client.put("/api/v1/themes/$themeId/bests/$bestId") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"items":[{"rank":1,"name":"a"},{"rank":1,"name":"b"}]}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }
}
