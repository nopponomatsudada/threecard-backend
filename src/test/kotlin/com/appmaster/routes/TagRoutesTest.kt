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
}
