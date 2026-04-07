package com.appmaster.routes

import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.callid.*
import io.ktor.server.routing.*
import org.slf4j.LoggerFactory
import java.security.MessageDigest

/**
 * Structured audit logger for authentication events. Hashes deviceIds and
 * never logs raw secrets/tokens.
 *
 * Format (single line, key=value): kept simple so existing log shippers parse it.
 */
internal object AuthLogger {
    private val log = LoggerFactory.getLogger("com.appmaster.audit.auth")

    fun event(
        call: ApplicationCall,
        event: String,
        deviceId: String? = null,
        userId: String? = null,
        extra: Map<String, String> = emptyMap()
    ) {
        val parts = buildList {
            add("event=$event")
            if (deviceId != null) add("deviceIdHash=${hashShort(deviceId)}")
            if (userId != null) add("userId=$userId")
            add("remoteHost=${call.request.origin.remoteHost}")
            call.callId?.let { add("requestId=$it") }
            extra.forEach { (k, v) -> add("$k=$v") }
        }
        log.info(parts.joinToString(" "))
    }

    private fun hashShort(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return digest.take(8).joinToString("") { "%02x".format(it) }
    }
}
