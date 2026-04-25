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

class ModerationRoutesTest {

    @BeforeTest fun setup() = setupTestDatabase()
    @AfterTest fun teardown() = tearDownTestDatabase()

    private suspend fun HttpClient.createTheme(token: String, tagId: String = "music"): String {
        val response = post("/api/v1/themes") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"title":"Theme for $tagId","tagId":"$tagId"}""")
        }
        return Json.parseToJsonElement(response.bodyAsText())
            .jsonObject["data"]!!.jsonObject["id"]!!.jsonPrimitive.content
    }

    private suspend fun HttpClient.createBest(token: String, themeId: String): String {
        val response = post("/api/v1/themes/$themeId/bests") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"items":[{"rank":1,"name":"Item 1"}]}""")
        }
        return Json.parseToJsonElement(response.bodyAsText())
            .jsonObject["data"]!!.jsonObject["id"]!!.jsonPrimitive.content
    }

    @Test
    fun `GET pending bests returns list`() = testApplication {
        configureFullTestApp()
        val client = jsonClient()
        val cf = cfAccessHeader()
        val token = client.getToken("device-mod-001")
        val themeId = client.createTheme(token)
        client.createBest(token, themeId)

        val response = client.get("/api/v1/admin/moderation/bests?status=pending") {
            header(TEST_CF_HEADER, cf)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val data = Json.parseToJsonElement(response.bodyAsText()).jsonObject["data"]!!.jsonArray
        assertTrue(data.size >= 1)
        assertEquals("pending", data[0].jsonObject["moderationStatus"]!!.jsonPrimitive.content)
    }

    @Test
    fun `GET pending themes returns list`() = testApplication {
        configureFullTestApp()
        val client = jsonClient()
        val cf = cfAccessHeader()
        val token = client.getToken("device-mod-002")
        client.createTheme(token)

        val response = client.get("/api/v1/admin/moderation/themes?status=pending") {
            header(TEST_CF_HEADER, cf)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val data = Json.parseToJsonElement(response.bodyAsText()).jsonObject["data"]!!.jsonArray
        assertTrue(data.size >= 1)
        assertEquals("pending", data[0].jsonObject["moderationStatus"]!!.jsonPrimitive.content)
    }

    @Test
    fun `PATCH approve best changes status`() = testApplication {
        configureFullTestApp()
        val client = jsonClient()
        val cf = cfAccessHeader()
        val token = client.getToken("device-mod-003")
        val themeId = client.createTheme(token)
        approveAllContent() // approve the theme so we can focus on the best
        val bestId = client.createBest(token, themeId)

        val response = client.patch("/api/v1/admin/moderation/bests/$bestId") {
            header(TEST_CF_HEADER, cf)
            contentType(ContentType.Application.Json)
            setBody("""{"status":"approved"}""")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val data = Json.parseToJsonElement(response.bodyAsText()).jsonObject["data"]!!.jsonObject
        assertEquals("approved", data["moderationStatus"]!!.jsonPrimitive.content)
    }

    @Test
    fun `PATCH approve best writes audit log with reviewer name`() = testApplication {
        configureFullTestApp()
        val client = jsonClient()
        val cf = cfAccessHeader(email = "owner@example.com", name = "Owner")
        val token = client.getToken("device-mod-approve-audit")
        val themeId = client.createTheme(token)
        approveAllContent()
        val bestId = client.createBest(token, themeId)

        client.patch("/api/v1/admin/moderation/bests/$bestId") {
            header(TEST_CF_HEADER, cf)
            contentType(ContentType.Application.Json)
            setBody("""{"status":"approved","note":"looks good"}""")
        }

        val auditResponse = client.get("/api/v1/admin/audit-logs") {
            header(TEST_CF_HEADER, cf)
        }

        assertEquals(HttpStatusCode.OK, auditResponse.status)
        val logs = Json.parseToJsonElement(auditResponse.bodyAsText()).jsonObject["data"]!!.jsonArray
        assertTrue(logs.isNotEmpty())
        val first = logs[0].jsonObject
        assertEquals("approve", first["action"]!!.jsonPrimitive.content)
        assertEquals("best", first["targetType"]!!.jsonPrimitive.content)
        assertEquals(bestId, first["targetId"]!!.jsonPrimitive.content)
        assertEquals("looks good", first["note"]!!.jsonPrimitive.content)
        assertEquals("Owner <owner@example.com>", first["reviewer"]!!.jsonPrimitive.content)
    }

    @Test
    fun `PATCH reject best changes status`() = testApplication {
        configureFullTestApp()
        val client = jsonClient()
        val cf = cfAccessHeader()
        val token = client.getToken("device-mod-004")
        val themeId = client.createTheme(token)
        approveAllContent()
        val bestId = client.createBest(token, themeId)

        val response = client.patch("/api/v1/admin/moderation/bests/$bestId") {
            header(TEST_CF_HEADER, cf)
            contentType(ContentType.Application.Json)
            setBody("""{"status":"rejected"}""")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val data = Json.parseToJsonElement(response.bodyAsText()).jsonObject["data"]!!.jsonObject
        assertEquals("rejected", data["moderationStatus"]!!.jsonPrimitive.content)
    }

    @Test
    fun `PATCH approve theme changes status`() = testApplication {
        configureFullTestApp()
        val client = jsonClient()
        val cf = cfAccessHeader()
        val token = client.getToken("device-mod-005")
        val themeId = client.createTheme(token)

        val response = client.patch("/api/v1/admin/moderation/themes/$themeId") {
            header(TEST_CF_HEADER, cf)
            contentType(ContentType.Application.Json)
            setBody("""{"status":"approved"}""")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val data = Json.parseToJsonElement(response.bodyAsText()).jsonObject["data"]!!.jsonObject
        assertEquals("approved", data["moderationStatus"]!!.jsonPrimitive.content)
    }

    @Test
    fun `POST skip theme keeps status pending and writes audit log`() = testApplication {
        configureFullTestApp()
        val client = jsonClient()
        val cf = cfAccessHeader()
        val token = client.getToken("device-mod-skip-theme")
        val themeId = client.createTheme(token)

        val response = client.post("/api/v1/admin/moderation/themes/$themeId/skip") {
            header(TEST_CF_HEADER, cf)
            contentType(ContentType.Application.Json)
            setBody("""{"note":"need another reviewer"}""")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val data = Json.parseToJsonElement(response.bodyAsText()).jsonObject["data"]!!.jsonObject
        assertEquals("pending", data["moderationStatus"]!!.jsonPrimitive.content)

        val auditResponse = client.get("/api/v1/admin/audit-logs") {
            header(TEST_CF_HEADER, cf)
        }
        val logs = Json.parseToJsonElement(auditResponse.bodyAsText()).jsonObject["data"]!!.jsonArray
        val first = logs[0].jsonObject
        assertEquals("skip", first["action"]!!.jsonPrimitive.content)
        assertEquals("theme", first["targetType"]!!.jsonPrimitive.content)
        assertEquals(themeId, first["targetId"]!!.jsonPrimitive.content)
    }

    @Test
    fun `POST skip best accepts empty body`() = testApplication {
        configureFullTestApp()
        val client = jsonClient()
        val cf = cfAccessHeader()
        val token = client.getToken("device-mod-skip-best-empty")
        val themeId = client.createTheme(token)
        approveAllContent()
        val bestId = client.createBest(token, themeId)

        val response = client.post("/api/v1/admin/moderation/bests/$bestId/skip") {
            header(TEST_CF_HEADER, cf)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val data = Json.parseToJsonElement(response.bodyAsText()).jsonObject["data"]!!.jsonObject
        assertEquals("pending", data["moderationStatus"]!!.jsonPrimitive.content)
    }

    @Test
    fun `Admin API without cf-access header returns 401`() = testApplication {
        configureFullTestApp()

        val response = client.get("/api/v1/admin/moderation/bests?status=pending")

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `Admin API with invalid cf-access JWT returns 401`() = testApplication {
        configureFullTestApp()

        val response = client.get("/api/v1/admin/moderation/bests?status=pending") {
            header(TEST_CF_HEADER, "not-a-valid-jwt")
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `PENDING best does not appear in discover`() = testApplication {
        configureFullTestApp()
        val client = jsonClient()
        val token = client.getToken("device-mod-006")
        val themeId = client.createTheme(token)
        approveAllContent() // approve theme
        client.createBest(token, themeId) // best stays PENDING

        val response = client.get("/api/v1/discover") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val data = Json.parseToJsonElement(response.bodyAsText()).jsonObject["data"]!!.jsonArray
        assertEquals(0, data.size)
    }

    @Test
    fun `APPROVED best appears in discover`() = testApplication {
        configureFullTestApp()
        val client = jsonClient()
        val token = client.getToken("device-mod-007")
        val themeId = client.createTheme(token)
        val bestId = client.createBest(token, themeId)
        approveAllContent() // approve both theme and best

        val response = client.get("/api/v1/discover") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val data = Json.parseToJsonElement(response.bodyAsText()).jsonObject["data"]!!.jsonArray
        assertTrue(data.size >= 1)
        assertEquals(bestId, data[0].jsonObject["id"]!!.jsonPrimitive.content)
    }

    @Test
    fun `PENDING best appears in my bests`() = testApplication {
        configureFullTestApp()
        val client = jsonClient()
        val token = client.getToken("device-mod-008")
        val themeId = client.createTheme(token)
        client.createBest(token, themeId) // PENDING

        val response = client.get("/api/v1/users/me/bests") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val data = Json.parseToJsonElement(response.bodyAsText()).jsonObject["data"]!!.jsonArray
        assertTrue(data.size >= 1)
    }

    @Test
    fun `PATCH with invalid status returns 400`() = testApplication {
        configureFullTestApp()
        val client = jsonClient()
        val cf = cfAccessHeader()
        val token = client.getToken("device-mod-009")
        val themeId = client.createTheme(token)
        val bestId = client.createBest(token, themeId)

        val response = client.patch("/api/v1/admin/moderation/bests/$bestId") {
            header(TEST_CF_HEADER, cf)
            contentType(ContentType.Application.Json)
            setBody("""{"status":"pending"}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `PENDING theme does not appear in theme list`() = testApplication {
        configureFullTestApp()
        val client = jsonClient()
        val token = client.getToken("device-mod-010")
        client.createTheme(token) // PENDING

        val response = client.get("/api/v1/themes") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val data = Json.parseToJsonElement(response.bodyAsText()).jsonObject["data"]!!.jsonArray
        assertEquals(0, data.size)
    }
}
