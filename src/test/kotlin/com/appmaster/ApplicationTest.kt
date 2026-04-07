@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.appmaster

import com.appmaster.plugins.configureSerialization
import com.appmaster.routes.healthRoutes
import com.appmaster.routes.setupTestDatabase
import com.appmaster.routes.tearDownTestDatabase
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ApplicationTest {

    @BeforeTest fun setup() = setupTestDatabase()
    @AfterTest fun teardown() = tearDownTestDatabase()

    @Test
    fun `health live endpoint returns 200 without db`() = testApplication {
        application {
            configureSerialization()
            routing { healthRoutes() }
        }
        val response = client.get("/health/live")
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("healthy"))
    }

    @Test
    fun `health ready endpoint returns 200 when db is reachable`() = testApplication {
        application {
            configureSerialization()
            routing { healthRoutes() }
        }
        val response = client.get("/health/ready")
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("healthy"))
    }

    @Test
    fun `root endpoint returns API info`() = testApplication {
        application {
            configureSerialization()
            routing { healthRoutes() }
        }
        val response = client.get("/")
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("AppMaster Backend API"))
    }
}
