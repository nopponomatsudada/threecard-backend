package com.appmaster.routes

import com.appmaster.data.dbQuery
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory

@Serializable
data class HealthResponse(
    val status: String,
    val version: String,
    val timestamp: Long
)

private val healthLog = LoggerFactory.getLogger("com.appmaster.routes.HealthRoutes")

fun Route.healthRoutes() {
    /** Liveness — process is up. Cheap, no DB. */
    get("/health/live") {
        call.respond(HttpStatusCode.OK, healthy())
    }

    /** Readiness — process is up AND can serve requests (DB reachable). */
    get("/health/ready") {
        if (probeDatabase()) {
            call.respond(HttpStatusCode.OK, healthy())
        } else {
            call.respond(HttpStatusCode.ServiceUnavailable, unhealthy())
        }
    }

    /** Back-compat: existing ALB target uses /health → readiness probe. */
    get("/health") {
        if (probeDatabase()) {
            call.respond(HttpStatusCode.OK, healthy())
        } else {
            call.respond(HttpStatusCode.ServiceUnavailable, unhealthy())
        }
    }

    get("/") {
        call.respondText("AppMaster Backend API v1.0.0", ContentType.Text.Plain)
    }
}

private suspend fun probeDatabase(): Boolean = runCatching {
    dbQuery {
        // Inside newSuspendedTransaction; org.jetbrains.exposed.v1.jdbc.Transaction is the
        // current receiver but Kotlin doesn't expose it on the lambda, so use exec directly.
        org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager.current().exec("SELECT 1")
    }
    true
}.getOrElse {
    healthLog.warn("Health DB probe failed: ${it.message}")
    false
}

private fun healthy() = HealthResponse("healthy", "1.0.0", System.currentTimeMillis())
private fun unhealthy() = HealthResponse("unhealthy", "1.0.0", System.currentTimeMillis())
