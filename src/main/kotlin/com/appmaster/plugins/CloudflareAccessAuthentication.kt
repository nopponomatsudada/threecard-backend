package com.appmaster.plugins

import com.auth0.jwk.JwkProviderBuilder
import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.JWTVerificationException
import com.auth0.jwt.interfaces.DecodedJWT
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import org.slf4j.LoggerFactory
import java.net.URI
import java.security.interfaces.RSAPublicKey
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

private val cfAccessLog = LoggerFactory.getLogger("com.appmaster.auth.cfaccess")

internal const val CF_JWT_HEADER = "Cf-Access-Jwt-Assertion"
private const val CF_JWT_COOKIE = "CF_Authorization"
internal const val CF_CLAIM_EMAIL = "email"
internal const val CF_CLAIM_NAME = "name"

data class CloudflareAccessConfig(
    val teamDomain: String,
    val audTag: String,
) {
    val issuer: String = "https://$teamDomain"
    val jwksUrl: String = "https://$teamDomain/cdn-cgi/access/certs"

    val isEnabled: Boolean get() = teamDomain.isNotBlank() && audTag.isNotBlank()
}

/**
 * Principal carried for routes authenticated via Cloudflare Access.
 *
 * Constructed purely from the Cloudflare Access JWT claims — no DB lookup. The
 * Cloudflare Access policy is the single source of truth for which emails are
 * admins; reaching this code already means the request passed the policy.
 */
data class AdminPrincipal(
    val adminId: String,
    val email: String,
    val displayName: String,
) {
    /** Human-readable identifier for audit logs / reviewer columns. */
    val label: String get() = if (displayName != email) "$displayName <$email>" else email
}

fun interface CfAccessTokenVerifier {
    fun verify(token: String): DecodedJWT
}

class CfAccessAuthenticationProvider internal constructor(
    config: Config,
) : AuthenticationProvider(config) {

    private val verifier = config.verifier

    override suspend fun onAuthenticate(context: AuthenticationContext) {
        val call = context.call
        val token = call.request.header(CF_JWT_HEADER)
            ?: call.request.cookies[CF_JWT_COOKIE]

        if (token.isNullOrBlank()) {
            context.fail(AuthenticationFailedCause.NoCredentials, "Cloudflare Access JWT が必要です")
            return
        }

        val decoded = try {
            verifier.verify(token)
        } catch (e: JWTVerificationException) {
            cfAccessLog.warn("CF Access JWT verification failed: {}", e.message)
            context.fail(AuthenticationFailedCause.InvalidCredentials, "Cloudflare Access JWT が無効です")
            return
        } catch (e: Exception) {
            // JWK fetch / network / configuration errors — log loudly so
            // misconfiguration is obvious, return 401 rather than 500.
            cfAccessLog.error("CF Access JWT verification errored unexpectedly: {} {}", e::class.simpleName, e.message)
            context.fail(AuthenticationFailedCause.Error(e.message ?: "verification error"), "Cloudflare Access JWT の検証に失敗しました")
            return
        }

        val email = decoded.getClaim(CF_CLAIM_EMAIL).asString()?.lowercase()
        if (email.isNullOrBlank()) {
            cfAccessLog.warn("CF Access JWT missing email claim sub={}", decoded.subject)
            context.fail(AuthenticationFailedCause.InvalidCredentials, "認証情報に email が含まれていません")
            return
        }

        val adminId = decoded.subject?.takeIf { it.isNotBlank() } ?: email
        val displayName = decoded.getClaim(CF_CLAIM_NAME).asString()?.takeIf { it.isNotBlank() } ?: email

        context.principal(AdminPrincipal(adminId = adminId, email = email, displayName = displayName))
    }

    private fun AuthenticationContext.fail(cause: AuthenticationFailedCause, message: String) {
        challenge(CHALLENGE_KEY, cause) { ch, c ->
            c.respondUnauthorized(message)
            ch.complete()
        }
    }

    class Config(name: String?) : AuthenticationProvider.Config(name) {
        lateinit var verifier: CfAccessTokenVerifier
    }

    companion object {
        private const val CHALLENGE_KEY = "CfAccessAuth"
    }
}

internal fun Application.loadCloudflareAccessConfig(): CloudflareAccessConfig {
    val teamDomain = configValue("cfAccess.teamDomain", "CF_ACCESS_TEAM_DOMAIN", "")
    val audTag = configValue("cfAccess.audTag", "CF_ACCESS_AUD_TAG", "")
    return CloudflareAccessConfig(teamDomain = teamDomain, audTag = audTag)
}

/**
 * Production verifier backed by the team JWKS endpoint. Per-kid `JWTVerifier`
 * instances are cached so the per-request hot path is just a kid lookup +
 * `verify`, not a re-build of issuer/audience/leeway checks. The underlying
 * `JwkProvider` already caches public keys with a 24h TTL.
 *
 * If team domain / aud tag are missing the returned verifier rejects every
 * token, surfacing misconfiguration as 401 rather than silent open access.
 */
internal fun cloudflareJwksVerifier(cfConfig: CloudflareAccessConfig): CfAccessTokenVerifier {
    if (!cfConfig.isEnabled) {
        return CfAccessTokenVerifier { throw JWTVerificationException("CF Access not configured") }
    }
    // Pass the JWKS URL as a java.net.URL — the String constructor of
    // JwkProviderBuilder treats its argument as a domain and silently appends
    // "/.well-known/jwks.json", which would build a non-existent path on
    // Cloudflare's certs endpoint.
    val jwkProvider = JwkProviderBuilder(URI(cfConfig.jwksUrl).toURL())
        .cached(10, 24, TimeUnit.HOURS)
        .rateLimited(10, 1, TimeUnit.MINUTES)
        .build()
    val verifierByKid = ConcurrentHashMap<String, JWTVerifier>()
    return CfAccessTokenVerifier { token ->
        val unverified = JWT.decode(token)
        val verifier = verifierByKid.computeIfAbsent(unverified.keyId) { kid ->
            val jwk = jwkProvider.get(kid)
            JWT.require(Algorithm.RSA256(jwk.publicKey as RSAPublicKey, null))
                .withIssuer(cfConfig.issuer)
                .withAudience(cfConfig.audTag)
                .acceptLeeway(30)
                .build()
        }
        verifier.verify(token)
    }
}

/**
 * Registers the `cf-access` realm. Routes wrapped with `authenticate("cf-access") { ... }`
 * require a valid Cloudflare Access JWT; the email/name/sub claims become the
 * [AdminPrincipal]. Cloudflare's Access policy decides who is an admin —
 * the backend keeps no allowlist.
 */
fun AuthenticationConfig.installCfAccess(verifier: CfAccessTokenVerifier) {
    register(
        CfAccessAuthenticationProvider(
            CfAccessAuthenticationProvider.Config("cf-access").apply {
                this.verifier = verifier
            }
        )
    )
}
