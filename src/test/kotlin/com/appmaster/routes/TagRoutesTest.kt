@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.appmaster.routes

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

class TagRoutesTest {

    @BeforeTest fun setup() = setupTestDatabase()
    @AfterTest fun teardown() = tearDownTestDatabase()

    @Test
    fun `GET tags requires authentication`() = testApplication {
        configureFullTestApp()
        val client = jsonClient()
        val response = client.get("/api/v1/tags")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `GET tags with valid JWT returns 200 with 8 tags`() = testApplication {
        configureFullTestApp()
        val client = jsonClient()
        val token = client.getToken("device-tags-001")

        val response = client.get("/api/v1/tags") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val data = Json.parseToJsonElement(response.bodyAsText())
            .jsonObject["data"]!!.jsonArray
        assertEquals(8, data.size)
    }

    @Test
    fun `GET tags returns all expected tag ids`() = testApplication {
        configureFullTestApp()
        val client = jsonClient()
        val token = client.getToken("device-tags-002")

        val response = client.get("/api/v1/tags") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        val ids = Json.parseToJsonElement(response.bodyAsText())
            .jsonObject["data"]!!.jsonArray
            .map { it.jsonObject["id"]!!.jsonPrimitive.content }

        assertTrue(ids.containsAll(listOf("music", "lifestyle", "books", "fashion", "food", "movies", "travel", "gadgets")))
    }

    @Test
    fun `GET tags returns label field for every tag matching OpenAPI`() = testApplication {
        configureFullTestApp()
        val client = jsonClient()
        val token = client.getToken("device-tags-003")

        val response = client.get("/api/v1/tags") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        val data = Json.parseToJsonElement(response.bodyAsText())
            .jsonObject["data"]!!.jsonArray

        // Every tag must expose `label` (OpenAPI contract) and it must be non-empty Japanese text.
        assertEquals(8, data.size)
        data.forEach { element ->
            val obj = element.jsonObject
            assertTrue(obj.containsKey("label"), "tag missing 'label' field: $obj")
            assertTrue(!obj.containsKey("name"), "tag should not expose legacy 'name' field: $obj")
            val label = obj["label"]!!.jsonPrimitive.content
            assertTrue(label.isNotBlank(), "tag label is blank: $obj")
        }

        // Spot-check that "music" maps to "音楽".
        val music = data.first { it.jsonObject["id"]!!.jsonPrimitive.content == "music" }.jsonObject
        assertEquals("音楽", music["label"]!!.jsonPrimitive.content)
    }
}
