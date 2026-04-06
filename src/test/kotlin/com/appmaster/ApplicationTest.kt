package com.appmaster

import com.appmaster.plugins.configureSerialization
import com.appmaster.routes.healthRoutes
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ApplicationTest {

    @Test
    fun `health endpoint returns healthy status`() = testApplication {
        application {
            configureSerialization()
            routing {
                healthRoutes()
            }
        }

        val response = client.get("/health")
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("healthy"))
    }

    @Test
    fun `root endpoint returns API info`() = testApplication {
        application {
            configureSerialization()
            routing {
                healthRoutes()
            }
        }

        val response = client.get("/")
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("AppMaster Backend API"))
    }
}
