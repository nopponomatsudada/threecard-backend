@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.appmaster.plugins

import com.appmaster.domain.repository.JwtBlocklistRepository
import com.appmaster.domain.repository.RefreshTokenRepository
import io.ktor.server.application.*
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.koin.ktor.ext.get
import org.slf4j.LoggerFactory
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

/**
 * Periodically purges expired rows from `refresh_tokens` and `jwt_blocklist`.
 *
 * Without this, both tables grow unboundedly: refresh tokens accumulate every
 * 7 days per device, blocklist entries every logout. The single-instance design
 * keeps this in-process; if the backend ever scales horizontally this should
 * move to a dedicated cron / Lambda.
 */
private val log = LoggerFactory.getLogger("com.appmaster.plugins.TokenCleanupScheduler")

fun Application.configureTokenCleanup(
    interval: Duration = 1.hours
) {
    val refreshRepo: RefreshTokenRepository = get()
    val blocklistRepo: JwtBlocklistRepository = get()

    val scope = this // Application is itself a CoroutineScope
    val job = scope.launch(
        Dispatchers.IO + CoroutineName("token-cleanup") + SupervisorJob()
    ) {
        while (isActive) {
            try {
                val now = Clock.System.now()
                refreshRepo.deleteExpired(now)
                blocklistRepo.deleteExpired(now)
                log.debug("Token cleanup pass completed")
            } catch (e: Exception) {
                // Never let a transient DB hiccup kill the loop.
                log.warn("Token cleanup pass failed: {}", e.message)
            }
            delay(interval)
        }
    }

    monitor.subscribe(ApplicationStopping) {
        job.cancel()
    }
}
