package com.appmaster.plugins

import io.ktor.server.application.*
import io.ktor.server.plugins.defaultheaders.*

/**
 * Configure security headers for the application.
 *
 * Sets standard security headers to protect against common web vulnerabilities:
 * - X-Frame-Options: Prevents clickjacking attacks
 * - X-Content-Type-Options: Prevents MIME type sniffing
 * - X-XSS-Protection: Enables browser XSS filter
 * - Strict-Transport-Security: Enforces HTTPS connections
 * - Content-Security-Policy: Controls resource loading
 * - Referrer-Policy: Controls referrer information
 * - Permissions-Policy: Controls browser features
 */
fun Application.configureSecurity() {
    install(DefaultHeaders) {
        // Prevent clickjacking - deny all framing
        header("X-Frame-Options", "DENY")

        // Prevent MIME type sniffing
        header("X-Content-Type-Options", "nosniff")

        // Enable XSS filter in browsers
        header("X-XSS-Protection", "1; mode=block")

        // Enforce HTTPS for 1 year, including subdomains
        header("Strict-Transport-Security", "max-age=31536000; includeSubDomains; preload")

        // Content Security Policy - API only, no inline scripts
        header("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'")

        // Control referrer information
        header("Referrer-Policy", "strict-origin-when-cross-origin")

        // Disable unnecessary browser features
        header("Permissions-Policy", "geolocation=(), microphone=(), camera=()")

        // Remove server header to hide implementation details
        header("Server", "")
    }
}
