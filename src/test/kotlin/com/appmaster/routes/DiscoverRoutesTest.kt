@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.appmaster.routes

import com.appmaster.data.entity.BestItemsTable
import com.appmaster.data.entity.BestsTable
import com.appmaster.data.entity.CollectionCardsTable
import com.appmaster.data.entity.CollectionsTable
import com.appmaster.data.entity.ThemesTable
import com.appmaster.data.entity.UsersTable
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DiscoverRoutesTest {

    @BeforeTest
    fun setup() {
        Database.connect("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1", driver = "org.h2.Driver")
        transaction {
            SchemaUtils.create(UsersTable, ThemesTable, BestsTable, BestItemsTable, CollectionsTable, CollectionCardsTable)
        }
    }

    @AfterTest
    fun teardown() {
        transaction {
            SchemaUtils.drop(CollectionCardsTable, CollectionsTable, BestItemsTable, BestsTable, ThemesTable, UsersTable)
        }
    }

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
