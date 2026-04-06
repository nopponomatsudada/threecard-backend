package com.appmaster.routes

import com.appmaster.plugins.configureSerialization
import com.appmaster.plugins.configureStatusPages
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.ktor.client.plugins.contentnegotiation.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TagRoutesTest {

    private fun ApplicationTestBuilder.configureTestApp() {
        application {
            configureSerialization()
            configureStatusPages()
            routing { tagRoutes() }
        }
    }

    private fun ApplicationTestBuilder.jsonClient() = createClient {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    @Test
    fun `GET tags returns 200 with 8 tags`() = testApplication {
        configureTestApp()
        val client = jsonClient()

        val response = client.get("/api/v1/tags")

        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        val data = body["data"]!!.jsonArray
        assertEquals(8, data.size)
    }

    @Test
    fun `GET tags returns all expected tag ids`() = testApplication {
        configureTestApp()
        val client = jsonClient()

        val response = client.get("/api/v1/tags")
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        val ids = body["data"]!!.jsonArray.map { it.jsonObject["id"]!!.jsonPrimitive.content }

        assertTrue(ids.containsAll(listOf("music", "lifestyle", "books", "fashion", "food", "movies", "travel", "gadgets")))
    }

    @Test
    fun `GET tags requires no auth`() = testApplication {
        configureTestApp()
        val client = jsonClient()

        val response = client.get("/api/v1/tags")
        assertEquals(HttpStatusCode.OK, response.status)
    }
}
