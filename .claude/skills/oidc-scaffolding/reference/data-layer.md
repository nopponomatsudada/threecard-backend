# OIDC Data 層リファレンス

OIDC 認証に関する Data 層のコードテンプレートです。

## OIDCAccountsTable.kt

```kotlin
package {{package}}.data.database

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp

/**
 * OIDC アカウントテーブル
 *
 * ユーザーと OIDC プロバイダーのリンク情報を保存
 */
object OIDCAccountsTable : Table("oidc_accounts") {
    val id = varchar("id", 36)
    val userId = varchar("user_id", 36).references(UsersTable.id)
    val provider = varchar("provider", 50)
    val providerUserId = varchar("provider_user_id", 255)
    val email = varchar("email", 255).nullable()
    val name = varchar("name", 255).nullable()
    val avatarUrl = varchar("avatar_url", 500).nullable()
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")

    override val primaryKey = PrimaryKey(id)

    init {
        // プロバイダーとプロバイダーユーザーIDの組み合わせは一意
        uniqueIndex("idx_oidc_provider_user", provider, providerUserId)
        // ユーザーとプロバイダーの組み合わせも一意（1ユーザー1プロバイダー）
        uniqueIndex("idx_oidc_user_provider", userId, provider)
        // ユーザーIDでの検索用インデックス
        index("idx_oidc_user_id", false, userId)
    }
}
```

## OIDCStatesTable.kt

```kotlin
package {{package}}.data.database

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp

/**
 * OIDC 認証状態テーブル
 *
 * CSRF/PKCE 対策のための一時的な状態を保存
 * 使用後または期限切れ後に削除される
 */
object OIDCStatesTable : Table("oidc_states") {
    val id = varchar("id", 36)
    val state = varchar("state", 64).uniqueIndex()
    val provider = varchar("provider", 50)
    val codeChallenge = varchar("code_challenge", 128)
    val redirectUri = varchar("redirect_uri", 500)
    val nonce = varchar("nonce", 64).nullable()
    val expiresAt = timestamp("expires_at")
    val createdAt = timestamp("created_at")
    val consumedAt = timestamp("consumed_at").nullable()

    override val primaryKey = PrimaryKey(id)

    init {
        // 期限切れ状態のクリーンアップ用インデックス
        index("idx_oidc_states_expires", false, expiresAt)
    }
}
```

## OIDCClient.kt (共通インターフェース)

```kotlin
package {{package}}.data.oidc

import {{package}}.domain.model.*

/**
 * OIDC クライアントインターフェース
 *
 * プロバイダー固有の実装を抽象化
 */
interface OIDCClient {
    val provider: OIDCProvider

    /**
     * 認証 URL を構築
     */
    fun buildAuthorizationUrl(
        state: String,
        codeChallenge: String,
        redirectUri: String,
        nonce: String? = null,
        scopes: List<String> = listOf("openid", "email", "profile")
    ): String

    /**
     * 認可コードをトークンに交換
     */
    suspend fun exchangeCodeForTokens(
        code: String,
        codeVerifier: String,
        redirectUri: String
    ): OIDCTokenResponse

    /**
     * ID Token を検証
     */
    suspend fun validateIdToken(
        idToken: String,
        nonce: String? = null
    ): OIDCIdTokenClaims

    /**
     * UserInfo を取得
     */
    suspend fun getUserInfo(accessToken: String): OIDCUserInfo
}
```

## GoogleOIDCClient.kt

```kotlin
package {{package}}.data.oidc

import com.auth0.jwk.JwkProviderBuilder
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import {{package}}.domain.error.DomainError
import {{package}}.domain.model.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.net.URL
import java.security.interfaces.RSAPublicKey
import java.util.concurrent.TimeUnit

/**
 * Google OIDC クライアント
 */
class GoogleOIDCClient(
    private val httpClient: HttpClient,
    private val config: GoogleOIDCConfig
) : OIDCClient {

    override val provider = OIDCProvider.GOOGLE

    private val jwkProvider = JwkProviderBuilder(URL(JWKS_URL))
        .cached(10, 24, TimeUnit.HOURS)
        .rateLimited(10, 1, TimeUnit.MINUTES)
        .build()

    companion object {
        const val AUTHORIZATION_URL = "https://accounts.google.com/o/oauth2/v2/auth"
        const val TOKEN_URL = "https://oauth2.googleapis.com/token"
        const val JWKS_URL = "https://www.googleapis.com/oauth2/v3/certs"
        const val USERINFO_URL = "https://openidconnect.googleapis.com/v1/userinfo"
        const val ISSUER = "https://accounts.google.com"
    }

    override fun buildAuthorizationUrl(
        state: String,
        codeChallenge: String,
        redirectUri: String,
        nonce: String?,
        scopes: List<String>
    ): String {
        return URLBuilder(AUTHORIZATION_URL).apply {
            parameters.append("client_id", config.clientId)
            parameters.append("redirect_uri", redirectUri)
            parameters.append("response_type", "code")
            parameters.append("scope", scopes.joinToString(" "))
            parameters.append("state", state)
            parameters.append("code_challenge", codeChallenge)
            parameters.append("code_challenge_method", "S256")
            parameters.append("access_type", "offline")
            parameters.append("prompt", "consent")
            nonce?.let { parameters.append("nonce", it) }
        }.buildString()
    }

    override suspend fun exchangeCodeForTokens(
        code: String,
        codeVerifier: String,
        redirectUri: String
    ): OIDCTokenResponse {
        val response: GoogleTokenResponse = httpClient.submitForm(
            url = TOKEN_URL,
            formParameters = parameters {
                append("client_id", config.clientId)
                append("client_secret", config.clientSecret)
                append("code", code)
                append("code_verifier", codeVerifier)
                append("grant_type", "authorization_code")
                append("redirect_uri", redirectUri)
            }
        ).body()

        return OIDCTokenResponse(
            accessToken = response.accessToken,
            idToken = response.idToken,
            refreshToken = response.refreshToken,
            expiresIn = response.expiresIn,
            tokenType = response.tokenType
        )
    }

    override suspend fun validateIdToken(
        idToken: String,
        nonce: String?
    ): OIDCIdTokenClaims {
        try {
            val decodedJwt = JWT.decode(idToken)

            // JWKS から公開鍵を取得
            val jwk = jwkProvider.get(decodedJwt.keyId)
            val publicKey = jwk.publicKey as RSAPublicKey
            val algorithm = Algorithm.RSA256(publicKey, null)

            // 署名検証
            val verifier = JWT.require(algorithm)
                .withIssuer(ISSUER)
                .withAudience(config.clientId)
                .apply {
                    // iOS/Android クライアントIDも許可
                    config.iosClientId?.let { withAudience(it) }
                    config.androidClientId?.let { withAudience(it) }
                }
                .build()

            val verified = verifier.verify(idToken)

            // Nonce 検証
            if (nonce != null) {
                val tokenNonce = verified.getClaim("nonce").asString()
                if (tokenNonce != nonce) {
                    throw DomainError.OIDC.invalidIdToken()
                }
            }

            return OIDCIdTokenClaims(
                subject = verified.subject,
                issuer = verified.issuer,
                audience = verified.audience.firstOrNull() ?: "",
                email = verified.getClaim("email").asString(),
                emailVerified = verified.getClaim("email_verified").asBoolean(),
                name = verified.getClaim("name").asString(),
                picture = verified.getClaim("picture").asString(),
                nonce = verified.getClaim("nonce").asString(),
                issuedAt = verified.issuedAt.time / 1000,
                expiresAt = verified.expiresAt.time / 1000
            )
        } catch (e: Exception) {
            when (e) {
                is DomainError -> throw e
                else -> throw DomainError.OIDC.invalidIdToken()
            }
        }
    }

    override suspend fun getUserInfo(accessToken: String): OIDCUserInfo {
        val response: GoogleUserInfo = httpClient.get(USERINFO_URL) {
            bearerAuth(accessToken)
        }.body()

        return OIDCUserInfo(
            subject = response.sub,
            email = response.email,
            emailVerified = response.emailVerified,
            name = response.name,
            givenName = response.givenName,
            familyName = response.familyName,
            picture = response.picture,
            locale = response.locale
        )
    }

    @Serializable
    private data class GoogleTokenResponse(
        @SerialName("access_token") val accessToken: String,
        @SerialName("id_token") val idToken: String,
        @SerialName("refresh_token") val refreshToken: String? = null,
        @SerialName("expires_in") val expiresIn: Int,
        @SerialName("token_type") val tokenType: String = "Bearer"
    )

    @Serializable
    private data class GoogleUserInfo(
        val sub: String,
        val email: String? = null,
        @SerialName("email_verified") val emailVerified: Boolean? = null,
        val name: String? = null,
        @SerialName("given_name") val givenName: String? = null,
        @SerialName("family_name") val familyName: String? = null,
        val picture: String? = null,
        val locale: String? = null
    )
}

data class GoogleOIDCConfig(
    val clientId: String,
    val clientSecret: String,
    val iosClientId: String? = null,
    val androidClientId: String? = null
)
```

## AppleOIDCClient.kt

```kotlin
package {{package}}.data.oidc

import com.auth0.jwk.JwkProviderBuilder
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import {{package}}.domain.error.DomainError
import {{package}}.domain.model.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.net.URL
import java.security.KeyFactory
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * Apple OIDC クライアント
 *
 * Apple 固有の特徴:
 * - Client Secret は JWT（Private Key で署名）
 * - Private Email Relay でメールアドレスが隠される場合がある
 * - 初回ログイン時のみユーザー名が取得可能
 */
class AppleOIDCClient(
    private val httpClient: HttpClient,
    private val config: AppleOIDCConfig
) : OIDCClient {

    override val provider = OIDCProvider.APPLE

    private val jwkProvider = JwkProviderBuilder(URL(JWKS_URL))
        .cached(10, 24, TimeUnit.HOURS)
        .rateLimited(10, 1, TimeUnit.MINUTES)
        .build()

    companion object {
        const val AUTHORIZATION_URL = "https://appleid.apple.com/auth/authorize"
        const val TOKEN_URL = "https://appleid.apple.com/auth/token"
        const val JWKS_URL = "https://appleid.apple.com/auth/keys"
        const val ISSUER = "https://appleid.apple.com"

        // Client Secret JWT の有効期限（最大6ヶ月）
        const val CLIENT_SECRET_EXPIRY_SECONDS = 15777000L // 約6ヶ月
    }

    override fun buildAuthorizationUrl(
        state: String,
        codeChallenge: String,
        redirectUri: String,
        nonce: String?,
        scopes: List<String>
    ): String {
        return URLBuilder(AUTHORIZATION_URL).apply {
            parameters.append("client_id", config.clientId)
            parameters.append("redirect_uri", redirectUri)
            parameters.append("response_type", "code")
            parameters.append("scope", scopes.joinToString(" "))
            parameters.append("state", state)
            parameters.append("response_mode", "form_post")
            nonce?.let { parameters.append("nonce", it) }
        }.buildString()
    }

    override suspend fun exchangeCodeForTokens(
        code: String,
        codeVerifier: String,
        redirectUri: String
    ): OIDCTokenResponse {
        val clientSecret = generateClientSecret()

        val response: AppleTokenResponse = httpClient.submitForm(
            url = TOKEN_URL,
            formParameters = parameters {
                append("client_id", config.clientId)
                append("client_secret", clientSecret)
                append("code", code)
                append("grant_type", "authorization_code")
                append("redirect_uri", redirectUri)
            }
        ).body()

        return OIDCTokenResponse(
            accessToken = response.accessToken,
            idToken = response.idToken,
            refreshToken = response.refreshToken,
            expiresIn = response.expiresIn,
            tokenType = response.tokenType
        )
    }

    override suspend fun validateIdToken(
        idToken: String,
        nonce: String?
    ): OIDCIdTokenClaims {
        try {
            val decodedJwt = JWT.decode(idToken)

            // JWKS から公開鍵を取得
            val jwk = jwkProvider.get(decodedJwt.keyId)
            val publicKey = jwk.publicKey as RSAPublicKey
            val algorithm = Algorithm.RSA256(publicKey, null)

            // 署名検証
            val verifier = JWT.require(algorithm)
                .withIssuer(ISSUER)
                .withAudience(config.clientId)
                .build()

            val verified = verifier.verify(idToken)

            // Nonce 検証
            if (nonce != null) {
                val tokenNonce = verified.getClaim("nonce").asString()
                if (tokenNonce != nonce) {
                    throw DomainError.OIDC.invalidIdToken()
                }
            }

            return OIDCIdTokenClaims(
                subject = verified.subject,
                issuer = verified.issuer,
                audience = verified.audience.firstOrNull() ?: "",
                email = verified.getClaim("email").asString(),
                emailVerified = verified.getClaim("email_verified").asBoolean(),
                name = null, // Apple は ID Token に name を含まない
                picture = null,
                nonce = verified.getClaim("nonce").asString(),
                issuedAt = verified.issuedAt.time / 1000,
                expiresAt = verified.expiresAt.time / 1000
            )
        } catch (e: Exception) {
            when (e) {
                is DomainError -> throw e
                else -> throw DomainError.OIDC.invalidIdToken()
            }
        }
    }

    override suspend fun getUserInfo(accessToken: String): OIDCUserInfo {
        // Apple は UserInfo エンドポイントを提供しない
        throw UnsupportedOperationException("Apple does not provide UserInfo endpoint")
    }

    /**
     * Apple 用の Client Secret JWT を生成
     *
     * Apple は Client Secret として JWT を要求
     * Private Key で署名する必要がある
     */
    private fun generateClientSecret(): String {
        val now = System.currentTimeMillis()
        val expiresAt = Date(now + CLIENT_SECRET_EXPIRY_SECONDS * 1000)

        val privateKey = loadPrivateKey(config.privateKey)

        return JWT.create()
            .withIssuer(config.teamId)
            .withIssuedAt(Date(now))
            .withExpiresAt(expiresAt)
            .withAudience(ISSUER)
            .withSubject(config.clientId)
            .withKeyId(config.keyId)
            .sign(Algorithm.ECDSA256(null, privateKey))
    }

    private fun loadPrivateKey(privateKeyPem: String): ECPrivateKey {
        val keyContent = privateKeyPem
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replace("\\s".toRegex(), "")

        val keyBytes = Base64.getDecoder().decode(keyContent)
        val keySpec = PKCS8EncodedKeySpec(keyBytes)
        val keyFactory = KeyFactory.getInstance("EC")
        return keyFactory.generatePrivate(keySpec) as ECPrivateKey
    }

    @Serializable
    private data class AppleTokenResponse(
        @SerialName("access_token") val accessToken: String,
        @SerialName("id_token") val idToken: String,
        @SerialName("refresh_token") val refreshToken: String? = null,
        @SerialName("expires_in") val expiresIn: Int,
        @SerialName("token_type") val tokenType: String = "Bearer"
    )
}

data class AppleOIDCConfig(
    val clientId: String,
    val teamId: String,
    val keyId: String,
    val privateKey: String
)
```

## OIDCRepositoryImpl.kt

```kotlin
package {{package}}.data.repository

import {{package}}.data.database.DatabaseFactory.dbQuery
import {{package}}.data.database.OIDCAccountsTable
import {{package}}.data.database.OIDCStatesTable
import {{package}}.data.oidc.OIDCClient
import {{package}}.domain.error.DomainError
import {{package}}.domain.model.*
import {{package}}.domain.repository.OIDCRepository
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import java.security.SecureRandom
import java.time.Instant
import java.util.*

class OIDCRepositoryImpl(
    private val clients: Map<OIDCProvider, OIDCClient>,
    private val stateExpiryMinutes: Long = 10
) : OIDCRepository {

    private val secureRandom = SecureRandom()

    // ========================================
    // State 管理
    // ========================================

    override suspend fun createState(
        provider: OIDCProvider,
        codeChallenge: String,
        redirectUri: String,
        nonce: String?
    ): OIDCState {
        val stateValue = generateSecureToken(32)
        val now = Instant.now()
        val expiresAt = now.plusSeconds(stateExpiryMinutes * 60)
        val id = UUID.randomUUID().toString()

        dbQuery {
            OIDCStatesTable.insert {
                it[OIDCStatesTable.id] = id
                it[state] = stateValue
                it[OIDCStatesTable.provider] = provider.id
                it[OIDCStatesTable.codeChallenge] = codeChallenge
                it[OIDCStatesTable.redirectUri] = redirectUri
                it[OIDCStatesTable.nonce] = nonce
                it[OIDCStatesTable.expiresAt] = expiresAt
                it[createdAt] = now
            }
        }

        return OIDCState(
            id = OIDCStateId(id),
            state = stateValue,
            provider = provider,
            codeChallenge = codeChallenge,
            redirectUri = redirectUri,
            nonce = nonce,
            expiresAt = expiresAt,
            createdAt = now
        )
    }

    override suspend fun validateAndConsumeState(state: String): OIDCState? = dbQuery {
        val now = Instant.now()

        val row = OIDCStatesTable
            .selectAll()
            .where {
                (OIDCStatesTable.state eq state) and
                (OIDCStatesTable.expiresAt greater now) and
                (OIDCStatesTable.consumedAt.isNull())
            }
            .singleOrNull()
            ?: return@dbQuery null

        // 使用済みにマーク
        OIDCStatesTable.update({ OIDCStatesTable.state eq state }) {
            it[consumedAt] = now
        }

        val provider = OIDCProvider.fromId(row[OIDCStatesTable.provider])
            ?: return@dbQuery null

        OIDCState(
            id = OIDCStateId(row[OIDCStatesTable.id]),
            state = row[OIDCStatesTable.state],
            provider = provider,
            codeChallenge = row[OIDCStatesTable.codeChallenge],
            redirectUri = row[OIDCStatesTable.redirectUri],
            nonce = row[OIDCStatesTable.nonce],
            expiresAt = row[OIDCStatesTable.expiresAt],
            createdAt = row[OIDCStatesTable.createdAt]
        )
    }

    // ========================================
    // OIDC アカウント管理
    // ========================================

    override suspend fun findByProviderAndProviderUserId(
        provider: OIDCProvider,
        providerUserId: String
    ): OIDCAccount? = dbQuery {
        OIDCAccountsTable
            .selectAll()
            .where {
                (OIDCAccountsTable.provider eq provider.id) and
                (OIDCAccountsTable.providerUserId eq providerUserId)
            }
            .singleOrNull()
            ?.toOIDCAccount()
    }

    override suspend fun findByUserId(userId: UserId): List<OIDCAccount> = dbQuery {
        OIDCAccountsTable
            .selectAll()
            .where { OIDCAccountsTable.userId eq userId.value }
            .map { it.toOIDCAccount() }
    }

    override suspend fun findByUserIdAndProvider(
        userId: UserId,
        provider: OIDCProvider
    ): OIDCAccount? = dbQuery {
        OIDCAccountsTable
            .selectAll()
            .where {
                (OIDCAccountsTable.userId eq userId.value) and
                (OIDCAccountsTable.provider eq provider.id)
            }
            .singleOrNull()
            ?.toOIDCAccount()
    }

    override suspend fun save(account: OIDCAccount): OIDCAccount = dbQuery {
        val exists = OIDCAccountsTable
            .selectAll()
            .where { OIDCAccountsTable.id eq account.id.value }
            .count() > 0

        if (exists) {
            OIDCAccountsTable.update({ OIDCAccountsTable.id eq account.id.value }) {
                it[email] = account.email
                it[name] = account.name
                it[avatarUrl] = account.avatarUrl
                it[updatedAt] = Instant.now()
            }
        } else {
            OIDCAccountsTable.insert {
                it[id] = account.id.value
                it[userId] = account.userId.value
                it[provider] = account.provider.id
                it[providerUserId] = account.providerUserId
                it[email] = account.email
                it[name] = account.name
                it[avatarUrl] = account.avatarUrl
                it[createdAt] = account.createdAt
                it[updatedAt] = account.updatedAt
            }
        }

        account
    }

    override suspend fun delete(id: OIDCAccountId): Unit = dbQuery {
        OIDCAccountsTable.deleteWhere { OIDCAccountsTable.id eq id.value }
    }

    // ========================================
    // トークン交換・検証
    // ========================================

    override suspend fun exchangeCodeForTokens(
        provider: OIDCProvider,
        code: String,
        codeVerifier: String,
        redirectUri: String
    ): OIDCTokenResponse {
        val client = getClient(provider)
        return client.exchangeCodeForTokens(code, codeVerifier, redirectUri)
    }

    override suspend fun validateIdToken(
        provider: OIDCProvider,
        idToken: String,
        nonce: String?
    ): OIDCIdTokenClaims {
        val client = getClient(provider)
        return client.validateIdToken(idToken, nonce)
    }

    override suspend fun getUserInfo(
        provider: OIDCProvider,
        accessToken: String
    ): OIDCUserInfo {
        val client = getClient(provider)
        return client.getUserInfo(accessToken)
    }

    // ========================================
    // 認証 URL 生成
    // ========================================

    override fun buildAuthorizationUrl(
        provider: OIDCProvider,
        state: String,
        codeChallenge: String,
        redirectUri: String,
        nonce: String?,
        scopes: List<String>
    ): String {
        val client = getClient(provider)
        return client.buildAuthorizationUrl(state, codeChallenge, redirectUri, nonce, scopes)
    }

    // ========================================
    // Private Helpers
    // ========================================

    private fun getClient(provider: OIDCProvider): OIDCClient {
        return clients[provider]
            ?: throw DomainError.OIDC.providerNotEnabled(provider.id)
    }

    private fun generateSecureToken(byteLength: Int): String {
        val bytes = ByteArray(byteLength)
        secureRandom.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun ResultRow.toOIDCAccount(): OIDCAccount {
        val provider = OIDCProvider.fromId(this[OIDCAccountsTable.provider])
            ?: throw IllegalStateException("Unknown provider: ${this[OIDCAccountsTable.provider]}")

        return OIDCAccount(
            id = OIDCAccountId(this[OIDCAccountsTable.id]),
            userId = UserId(this[OIDCAccountsTable.userId]),
            provider = provider,
            providerUserId = this[OIDCAccountsTable.providerUserId],
            email = this[OIDCAccountsTable.email],
            name = this[OIDCAccountsTable.name],
            avatarUrl = this[OIDCAccountsTable.avatarUrl],
            createdAt = this[OIDCAccountsTable.createdAt],
            updatedAt = this[OIDCAccountsTable.updatedAt]
        )
    }
}
```

## UsersTable への変更

`password_hash` を nullable に変更:

```kotlin
object UsersTable : Table("users") {
    // ... 既存のカラム ...

    // nullable に変更（OIDC ユーザー対応）
    val passwordHash = varchar("password_hash", 255).nullable()

    // ... 残りのカラム ...
}
```

## DatabaseFactory への追加

マイグレーションに OIDC テーブルを追加:

```kotlin
fun createTables() {
    transaction {
        SchemaUtils.create(
            UsersTable,
            RefreshTokensTable,
            OIDCAccountsTable,  // 追加
            OIDCStatesTable,    // 追加
            // ... 他のテーブル
        )
    }
}
```
