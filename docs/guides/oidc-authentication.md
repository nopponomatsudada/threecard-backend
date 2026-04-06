# OIDC 認証ガイド

このドキュメントは、OpenID Connect (OIDC) を使用したソーシャルログイン認証の実装ガイドを提供します。

## 目次

1. [概要](#概要)
2. [対応プロバイダー](#対応プロバイダー)
3. [認証フロー](#認証フロー)
4. [アーキテクチャ](#アーキテクチャ)
5. [実装手順](#実装手順)
6. [コード例](#コード例)
7. [プロバイダー別設定](#プロバイダー別設定)
8. [セキュリティ考慮事項](#セキュリティ考慮事項)
9. [トラブルシューティング](#トラブルシューティング)

---

## 概要

OIDC (OpenID Connect) は OAuth 2.0 の上に構築された認証レイヤーです。ユーザーは Google や Apple などの外部プロバイダーを使用してログインできます。

### OIDC vs OAuth 2.0

| 項目 | OAuth 2.0 | OIDC |
|-----|-----------|------|
| 目的 | 認可（アクセス権限の付与） | 認証（ユーザーの識別） |
| トークン | Access Token のみ | Access Token + ID Token |
| ユーザー情報 | 別途 API 呼び出しが必要 | ID Token に含まれる |
| 標準化 | 実装が多様 | より標準化されている |

### メリット

- **UX 向上**: ワンクリックでログイン可能
- **セキュリティ**: パスワード管理が不要、プロバイダーのセキュリティを活用
- **信頼性**: 大手プロバイダーの認証基盤を利用
- **開発効率**: パスワードリセット機能などが不要

---

## 対応プロバイダー

### 優先実装（推奨）

| プロバイダー | プロトコル | 特記事項 |
|------------|----------|---------|
| **Google** | OIDC | 標準的な実装、幅広いユーザー基盤 |
| **Apple** | OIDC | iOS アプリでは App Store ガイドラインで必須の場合あり |

### 追加オプション

| プロバイダー | プロトコル | 特記事項 |
|------------|----------|---------|
| GitHub | OAuth 2.0 | ID Token なし、UserInfo API で取得 |
| LINE | OIDC | 日本市場向け |

---

## 認証フロー

### PKCE (Proof Key for Code Exchange) フロー

モバイルアプリではセキュリティ向上のため PKCE を使用します。

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         OIDC 認証フロー（PKCE）                               │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ┌───────────┐          ┌───────────┐          ┌───────────────┐            │
│  │  Mobile   │          │  Backend  │          │ OIDC Provider │            │
│  │    App    │          │    API    │          │ (Google等)    │            │
│  └─────┬─────┘          └─────┬─────┘          └───────┬───────┘            │
│        │                      │                        │                    │
│        │ 1. code_verifier 生成                          │                    │
│        │    code_challenge = SHA256(code_verifier)      │                    │
│        │                      │                        │                    │
│        │ 2. POST /oidc/{provider}/init                  │                    │
│        │    {code_challenge, redirect_uri}              │                    │
│        │─────────────────────►│                        │                    │
│        │                      │                        │                    │
│        │     {auth_url, state}│                        │                    │
│        │◄─────────────────────│                        │                    │
│        │                      │                        │                    │
│        │ 3. ブラウザで認証画面を開く                        │                    │
│        │────────────────────────────────────────────────►│                   │
│        │                      │                        │                    │
│        │ 4. ユーザーがログイン・同意                        │                    │
│        │                      │                        │                    │
│        │     code + state（リダイレクト）                  │                    │
│        │◄────────────────────────────────────────────────│                   │
│        │                      │                        │                    │
│        │ 5. POST /oidc/{provider}/callback              │                    │
│        │    {code, state, code_verifier}                │                    │
│        │─────────────────────►│                        │                    │
│        │                      │                        │                    │
│        │                      │ 6. トークン交換            │                    │
│        │                      │    code + code_verifier │                    │
│        │                      │───────────────────────►│                    │
│        │                      │                        │                    │
│        │                      │    {id_token, access_token}                  │
│        │                      │◄───────────────────────│                    │
│        │                      │                        │                    │
│        │                      │ 7. ID Token 検証         │                    │
│        │                      │    ユーザー作成/取得       │                    │
│        │                      │    JWT 生成             │                    │
│        │                      │                        │                    │
│        │     {accessToken, refreshToken, user}          │                    │
│        │◄─────────────────────│                        │                    │
│        │                      │                        │                    │
└─────────────────────────────────────────────────────────────────────────────┘
```

### ネイティブ SDK トークン交換フロー

Google Sign-In SDK や Sign in with Apple SDK を使用する場合のフローです。

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                   ネイティブ SDK トークン交換フロー                            │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ┌───────────┐          ┌───────────┐          ┌───────────────┐            │
│  │  Mobile   │          │  Backend  │          │ OIDC Provider │            │
│  │    App    │          │    API    │          │ (Google等)    │            │
│  └─────┬─────┘          └─────┬─────┘          └───────┬───────┘            │
│        │                      │                        │                    │
│        │ 1. ネイティブ SDK で認証                        │                    │
│        │────────────────────────────────────────────────►│                   │
│        │                      │                        │                    │
│        │     id_token（SDK から直接取得）                  │                   │
│        │◄────────────────────────────────────────────────│                   │
│        │                      │                        │                    │
│        │ 2. POST /oidc/{provider}/token                 │                    │
│        │    {id_token}                                  │                    │
│        │─────────────────────►│                        │                    │
│        │                      │                        │                    │
│        │                      │ 3. ID Token 検証         │                    │
│        │                      │    (JWKS で署名検証)      │                    │
│        │                      │    ユーザー作成/取得       │                    │
│        │                      │                        │                    │
│        │     {accessToken, refreshToken, user}          │                    │
│        │◄─────────────────────│                        │                    │
│        │                      │                        │                    │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## アーキテクチャ

### レイヤー構成

```
┌─────────────────────────────────────────────────────────────────┐
│                       Routes 層                                  │
│  ┌─────────────────────────────────────────────────────────────┐│
│  │                    OIDCRoutes.kt                             ││
│  │  POST /auth/oidc/{provider}/init     認証 URL 取得           ││
│  │  POST /auth/oidc/{provider}/callback コールバック処理          ││
│  │  POST /auth/oidc/{provider}/token    ネイティブトークン交換     ││
│  │  POST /auth/oidc/{provider}/link     アカウントリンク          ││
│  │  DELETE /auth/oidc/{provider}        リンク解除               ││
│  │  GET  /auth/oidc/accounts            リンク済み一覧            ││
│  └─────────────────────────────────────────────────────────────┘│
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                       Domain 層                                  │
│  ┌───────────────────┐ ┌───────────────────────────────────────┐│
│  │     Model         │ │            UseCase                     ││
│  │  - OIDCProvider   │ │  - InitiateOIDCUseCase                 ││
│  │  - OIDCAccount    │ │  - OIDCCallbackUseCase                 ││
│  │  - OIDCState      │ │  - ExchangeProviderTokenUseCase        ││
│  │  - OIDCTokens     │ │  - LinkOIDCProviderUseCase             ││
│  │  - OIDCUserInfo   │ │  - UnlinkOIDCProviderUseCase           ││
│  └───────────────────┘ └───────────────────────────────────────┘│
│  ┌─────────────────────────────────────────────────────────────┐│
│  │                OIDCRepository (interface)                    ││
│  └─────────────────────────────────────────────────────────────┘│
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                        Data 層                                   │
│  ┌───────────────────┐ ┌───────────────────────────────────────┐│
│  │    Database       │ │          Provider Clients              ││
│  │  - OIDCAccountsT. │ │  - GoogleOIDCClient                    ││
│  │  - OIDCStatesT.   │ │  - AppleOIDCClient                     ││
│  └───────────────────┘ │  - GitHubOAuthClient                   ││
│  ┌───────────────────┐ │  - LineOIDCClient                      ││
│  │ OIDCRepositoryImpl│ └───────────────────────────────────────┘│
│  └───────────────────┘                                          │
└─────────────────────────────────────────────────────────────────┘
```

### ファイル構成

```
src/main/kotlin/com/example/
├── domain/
│   ├── model/
│   │   ├── OIDCProvider.kt        # プロバイダー enum
│   │   ├── OIDCAccount.kt         # OIDC アカウントエンティティ
│   │   ├── OIDCState.kt           # 認証状態
│   │   └── OIDCTokens.kt          # OIDC トークン
│   ├── repository/
│   │   └── OIDCRepository.kt      # リポジトリインターフェース
│   └── usecase/oidc/
│       ├── InitiateOIDCUseCase.kt
│       ├── OIDCCallbackUseCase.kt
│       ├── ExchangeProviderTokenUseCase.kt
│       ├── LinkOIDCProviderUseCase.kt
│       └── UnlinkOIDCProviderUseCase.kt
├── data/
│   ├── database/
│   │   ├── OIDCAccountsTable.kt
│   │   └── OIDCStatesTable.kt
│   ├── repository/
│   │   └── OIDCRepositoryImpl.kt
│   └── oidc/
│       ├── OIDCClient.kt          # 共通インターフェース
│       ├── GoogleOIDCClient.kt
│       ├── AppleOIDCClient.kt
│       ├── GitHubOAuthClient.kt
│       └── LineOIDCClient.kt
├── routes/
│   ├── dto/
│   │   └── OIDCDto.kt
│   └── OIDCRoutes.kt
└── di/
    └── OIDCModule.kt
```

---

## 実装手順

### 1. 依存関係の追加

```kotlin
// build.gradle.kts
dependencies {
    // Ktor Client（プロバイダーとの通信用）
    implementation("io.ktor:ktor-client-core:$ktor_version")
    implementation("io.ktor:ktor-client-cio:$ktor_version")
    implementation("io.ktor:ktor-client-content-negotiation:$ktor_version")
    implementation("io.ktor:ktor-client-logging:$ktor_version")

    // JWKS（ID Token 検証用）
    implementation("com.auth0:jwks-rsa:0.22.1")
    implementation("com.auth0:java-jwt:4.4.0")
}
```

### 2. 設定ファイル

```hocon
# application.conf
oidc {
    stateExpiryMinutes = 10
    stateExpiryMinutes = ${?OIDC_STATE_EXPIRY_MINUTES}

    google {
        enabled = true
        enabled = ${?OIDC_GOOGLE_ENABLED}
        clientId = ${?GOOGLE_CLIENT_ID}
        clientSecret = ${?GOOGLE_CLIENT_SECRET}
        # iOS/Android クライアント ID（ネイティブ SDK 用）
        iosClientId = ${?GOOGLE_IOS_CLIENT_ID}
        androidClientId = ${?GOOGLE_ANDROID_CLIENT_ID}
    }

    apple {
        enabled = true
        enabled = ${?OIDC_APPLE_ENABLED}
        clientId = ${?APPLE_CLIENT_ID}
        teamId = ${?APPLE_TEAM_ID}
        keyId = ${?APPLE_KEY_ID}
        privateKey = ${?APPLE_PRIVATE_KEY}
    }

    # オプション
    github {
        enabled = false
        enabled = ${?OIDC_GITHUB_ENABLED}
        clientId = ${?GITHUB_CLIENT_ID}
        clientSecret = ${?GITHUB_CLIENT_SECRET}
    }

    line {
        enabled = false
        enabled = ${?OIDC_LINE_ENABLED}
        clientId = ${?LINE_CHANNEL_ID}
        clientSecret = ${?LINE_CHANNEL_SECRET}
    }
}
```

### 3. 実装順序

```
1. Domain 層
   ├── model/OIDCProvider.kt
   ├── model/OIDCAccount.kt
   ├── model/OIDCState.kt
   ├── model/OIDCTokens.kt
   └── repository/OIDCRepository.kt

2. Data 層
   ├── database/OIDCAccountsTable.kt
   ├── database/OIDCStatesTable.kt
   ├── oidc/GoogleOIDCClient.kt
   ├── oidc/AppleOIDCClient.kt
   └── repository/OIDCRepositoryImpl.kt

3. UseCase 層
   ├── InitiateOIDCUseCase.kt
   ├── OIDCCallbackUseCase.kt
   ├── ExchangeProviderTokenUseCase.kt
   ├── LinkOIDCProviderUseCase.kt
   └── UnlinkOIDCProviderUseCase.kt

4. Routes 層
   ├── dto/OIDCDto.kt
   └── OIDCRoutes.kt

5. DI 設定
   └── OIDCModule.kt

6. 既存コード変更
   ├── User.kt (passwordHash を nullable に)
   ├── DatabaseFactory.kt (テーブル追加)
   └── Routing.kt (ルート追加)
```

---

## コード例

### Domain 層

#### OIDCProvider.kt

```kotlin
package com.example.domain.model

/**
 * OIDC プロバイダー
 */
enum class OIDCProvider(val id: String, val displayName: String) {
    GOOGLE("google", "Google"),
    APPLE("apple", "Apple"),
    GITHUB("github", "GitHub"),
    LINE("line", "LINE");

    companion object {
        fun fromId(id: String): OIDCProvider? =
            entries.find { it.id == id }
    }
}
```

#### OIDCAccount.kt

```kotlin
package com.example.domain.model

import java.time.Instant

/**
 * OIDC プロバイダーとリンクされたアカウント
 */
data class OIDCAccount(
    val id: OIDCAccountId,
    val userId: UserId,
    val provider: OIDCProvider,
    val providerUserId: String,
    val email: String?,
    val name: String?,
    val avatarUrl: String?,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now()
)

@JvmInline
value class OIDCAccountId(val value: String)
```

#### OIDCRepository.kt

```kotlin
package com.example.domain.repository

import com.example.domain.model.*

interface OIDCRepository {
    // State 管理 (CSRF/PKCE)
    suspend fun createState(
        provider: OIDCProvider,
        codeChallenge: String,
        redirectUri: String,
        nonce: String? = null
    ): OIDCState

    suspend fun validateAndConsumeState(state: String): OIDCState?

    // OIDC アカウント管理
    suspend fun findByProviderAndProviderUserId(
        provider: OIDCProvider,
        providerUserId: String
    ): OIDCAccount?

    suspend fun findByUserId(userId: UserId): List<OIDCAccount>
    suspend fun save(account: OIDCAccount): OIDCAccount
    suspend fun delete(id: OIDCAccountId)

    // トークン交換・検証
    suspend fun exchangeCodeForTokens(
        provider: OIDCProvider,
        code: String,
        codeVerifier: String,
        redirectUri: String
    ): OIDCTokenResponse

    suspend fun validateIdToken(
        provider: OIDCProvider,
        idToken: String,
        nonce: String? = null
    ): OIDCIdTokenClaims

    suspend fun getUserInfo(
        provider: OIDCProvider,
        accessToken: String
    ): OIDCUserInfo
}
```

### UseCase 層

#### OIDCCallbackUseCase.kt

```kotlin
package com.example.domain.usecase.oidc

import com.example.domain.error.DomainError
import com.example.domain.model.*
import com.example.domain.repository.AuthRepository
import com.example.domain.repository.OIDCRepository
import com.example.domain.repository.UserRepository
import java.util.UUID

/**
 * OIDC コールバック処理
 *
 * 認可コードを受け取り、トークン交換とユーザー認証を行う
 * 同じメールアドレスの既存ユーザーがいる場合は自動的にリンク
 */
class OIDCCallbackUseCase(
    private val oidcRepository: OIDCRepository,
    private val userRepository: UserRepository,
    private val authRepository: AuthRepository
) {
    data class Input(
        val provider: OIDCProvider,
        val code: String,
        val state: String,
        val codeVerifier: String
    )

    data class Output(
        val accessToken: String,
        val refreshToken: String,
        val user: User,
        val isNewUser: Boolean
    )

    suspend operator fun invoke(input: Input): Output {
        // 1. State 検証（CSRF 対策）
        val storedState = oidcRepository.validateAndConsumeState(input.state)
            ?: throw DomainError.Unauthorized.invalidState()

        if (storedState.provider != input.provider) {
            throw DomainError.Unauthorized.invalidState()
        }

        // 2. 認可コードをトークンに交換
        val tokenResponse = oidcRepository.exchangeCodeForTokens(
            provider = input.provider,
            code = input.code,
            codeVerifier = input.codeVerifier,
            redirectUri = storedState.redirectUri
        )

        // 3. ID Token 検証
        val claims = oidcRepository.validateIdToken(
            provider = input.provider,
            idToken = tokenResponse.idToken,
            nonce = storedState.nonce
        )

        // 4. 既存の OIDC アカウントを検索
        val existingOIDCAccount = oidcRepository.findByProviderAndProviderUserId(
            provider = input.provider,
            providerUserId = claims.subject
        )

        // 5. ユーザー取得または作成
        val (user, isNewUser) = if (existingOIDCAccount != null) {
            // 既存の OIDC リンクがある → そのユーザーでログイン
            val user = userRepository.findById(existingOIDCAccount.userId)
                ?: throw DomainError.NotFound.user(existingOIDCAccount.userId.value)
            user to false
        } else {
            // 新規 OIDC ログイン
            resolveOrCreateUser(claims, input.provider)
        }

        // 6. OIDC アカウントが未作成なら作成
        if (existingOIDCAccount == null) {
            val oidcAccount = OIDCAccount(
                id = OIDCAccountId(UUID.randomUUID().toString()),
                userId = user.id,
                provider = input.provider,
                providerUserId = claims.subject,
                email = claims.email,
                name = claims.name,
                avatarUrl = claims.picture
            )
            oidcRepository.save(oidcAccount)
        }

        // 7. JWT トークン発行
        val tokens = authRepository.generateTokens(user)

        return Output(
            accessToken = tokens.accessToken,
            refreshToken = tokens.refreshToken,
            user = user,
            isNewUser = isNewUser
        )
    }

    /**
     * メールアドレスで既存ユーザーを探し、いなければ新規作成
     * 同じメールの既存ユーザーがいれば自動リンク
     */
    private suspend fun resolveOrCreateUser(
        claims: OIDCIdTokenClaims,
        provider: OIDCProvider
    ): Pair<User, Boolean> {
        // メールアドレスで既存ユーザーを検索
        val email = claims.email
        if (email != null) {
            val existingUser = userRepository.findByEmail(email)
            if (existingUser != null) {
                // 既存ユーザーに自動リンク
                return existingUser to false
            }
        }

        // 新規ユーザー作成
        val newUser = User(
            id = UserId(UUID.randomUUID().toString()),
            email = email?.let { Email(it) },
            username = generateUsername(claims, provider),
            displayName = claims.name ?: "User",
            avatarUrl = claims.picture,
            passwordHash = null, // OIDC ユーザーはパスワードなし
            emailVerified = claims.emailVerified ?: false
        )

        val savedUser = userRepository.save(newUser)
        return savedUser to true
    }

    private fun generateUsername(claims: OIDCIdTokenClaims, provider: OIDCProvider): Username {
        val base = claims.name?.replace(" ", "")?.lowercase()
            ?: "${provider.id}_${claims.subject.take(8)}"
        return Username("${base}_${UUID.randomUUID().toString().take(4)}")
    }
}
```

### Routes 層

#### OIDCRoutes.kt

```kotlin
package com.example.routes

import com.example.domain.model.OIDCProvider
import com.example.domain.usecase.oidc.*
import com.example.routes.dto.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

fun Route.oidcRoutes() {
    val initiateUseCase by inject<InitiateOIDCUseCase>()
    val callbackUseCase by inject<OIDCCallbackUseCase>()
    val exchangeTokenUseCase by inject<ExchangeProviderTokenUseCase>()
    val linkUseCase by inject<LinkOIDCProviderUseCase>()
    val unlinkUseCase by inject<UnlinkOIDCProviderUseCase>()
    val getLinkedAccountsUseCase by inject<GetLinkedOIDCAccountsUseCase>()

    route("/auth/oidc") {
        // 認証 URL 取得
        post("/{provider}/init") {
            val provider = call.parameters["provider"]
                ?.let { OIDCProvider.fromId(it) }
                ?: throw BadRequestException("Invalid provider")

            val request = call.receive<OIDCInitRequest>()

            val output = initiateUseCase(
                InitiateOIDCUseCase.Input(
                    provider = provider,
                    codeChallenge = request.codeChallenge,
                    redirectUri = request.redirectUri
                )
            )

            call.respond(
                OIDCInitResponse(
                    authUrl = output.authUrl,
                    state = output.state
                )
            )
        }

        // コールバック処理
        post("/{provider}/callback") {
            val provider = call.parameters["provider"]
                ?.let { OIDCProvider.fromId(it) }
                ?: throw BadRequestException("Invalid provider")

            val request = call.receive<OIDCCallbackRequest>()

            val output = callbackUseCase(
                OIDCCallbackUseCase.Input(
                    provider = provider,
                    code = request.code,
                    state = request.state,
                    codeVerifier = request.codeVerifier
                )
            )

            call.respond(
                HttpStatusCode.OK,
                AuthResponse(
                    accessToken = output.accessToken,
                    refreshToken = output.refreshToken,
                    user = output.user.toDto(),
                    isNewUser = output.isNewUser
                )
            )
        }

        // ネイティブ SDK トークン交換
        post("/{provider}/token") {
            val provider = call.parameters["provider"]
                ?.let { OIDCProvider.fromId(it) }
                ?: throw BadRequestException("Invalid provider")

            val request = call.receive<OIDCTokenExchangeRequest>()

            val output = exchangeTokenUseCase(
                ExchangeProviderTokenUseCase.Input(
                    provider = provider,
                    idToken = request.idToken,
                    nonce = request.nonce
                )
            )

            call.respond(
                HttpStatusCode.OK,
                AuthResponse(
                    accessToken = output.accessToken,
                    refreshToken = output.refreshToken,
                    user = output.user.toDto(),
                    isNewUser = output.isNewUser
                )
            )
        }

        // 認証必須エンドポイント
        authenticate("jwt") {
            // アカウントリンク
            post("/{provider}/link") {
                val provider = call.parameters["provider"]
                    ?.let { OIDCProvider.fromId(it) }
                    ?: throw BadRequestException("Invalid provider")

                val userId = call.principal<JWTPrincipal>()
                    ?.payload?.getClaim("userId")?.asString()
                    ?: throw UnauthorizedException("User not found")

                val request = call.receive<OIDCLinkRequest>()

                val output = linkUseCase(
                    LinkOIDCProviderUseCase.Input(
                        userId = UserId(userId),
                        provider = provider,
                        code = request.code,
                        state = request.state,
                        codeVerifier = request.codeVerifier
                    )
                )

                call.respond(
                    HttpStatusCode.OK,
                    OIDCAccountResponse(
                        provider = output.account.provider.id,
                        email = output.account.email,
                        linkedAt = output.account.createdAt.toString()
                    )
                )
            }

            // リンク解除
            delete("/{provider}") {
                val provider = call.parameters["provider"]
                    ?.let { OIDCProvider.fromId(it) }
                    ?: throw BadRequestException("Invalid provider")

                val userId = call.principal<JWTPrincipal>()
                    ?.payload?.getClaim("userId")?.asString()
                    ?: throw UnauthorizedException("User not found")

                unlinkUseCase(
                    UnlinkOIDCProviderUseCase.Input(
                        userId = UserId(userId),
                        provider = provider
                    )
                )

                call.respond(HttpStatusCode.NoContent)
            }

            // リンク済みアカウント一覧
            get("/accounts") {
                val userId = call.principal<JWTPrincipal>()
                    ?.payload?.getClaim("userId")?.asString()
                    ?: throw UnauthorizedException("User not found")

                val accounts = getLinkedAccountsUseCase(UserId(userId))

                call.respond(
                    accounts.map { account ->
                        OIDCAccountResponse(
                            provider = account.provider.id,
                            email = account.email,
                            linkedAt = account.createdAt.toString()
                        )
                    }
                )
            }
        }
    }
}
```

---

## プロバイダー別設定

### Google

#### 設定手順

1. [Google Cloud Console](https://console.cloud.google.com/) でプロジェクト作成
2. OAuth 同意画面を設定
3. OAuth 2.0 クライアント ID を作成
   - Web アプリケーション用
   - iOS アプリ用
   - Android アプリ用
4. 認証情報を環境変数に設定

#### 環境変数

```bash
GOOGLE_CLIENT_ID=xxx.apps.googleusercontent.com
GOOGLE_CLIENT_SECRET=GOCSPX-xxx
GOOGLE_IOS_CLIENT_ID=xxx.apps.googleusercontent.com
GOOGLE_ANDROID_CLIENT_ID=xxx.apps.googleusercontent.com
```

#### エンドポイント

```kotlin
object GoogleOIDCEndpoints {
    const val AUTHORIZATION = "https://accounts.google.com/o/oauth2/v2/auth"
    const val TOKEN = "https://oauth2.googleapis.com/token"
    const val JWKS = "https://www.googleapis.com/oauth2/v3/certs"
    const val USERINFO = "https://openidconnect.googleapis.com/v1/userinfo"
    const val ISSUER = "https://accounts.google.com"
}
```

### Apple

#### 設定手順

1. [Apple Developer](https://developer.apple.com/) でアプリ ID を作成
2. Sign in with Apple を有効化
3. Service ID を作成（Web 用）
4. Private Key を作成
5. 認証情報を環境変数に設定

#### 環境変数

```bash
APPLE_CLIENT_ID=com.example.app.signin
APPLE_TEAM_ID=XXXXXXXXXX
APPLE_KEY_ID=XXXXXXXXXX
APPLE_PRIVATE_KEY="-----BEGIN PRIVATE KEY-----\n...\n-----END PRIVATE KEY-----"
```

#### 特記事項

- Apple は Client Secret として JWT を使用（Private Key で署名）
- Private Email Relay: ユーザーが実際のメールアドレスを隠す場合がある
- 初回ログイン時のみユーザー名が取得可能

---

## セキュリティ考慮事項

### 必須の対策

| 対策 | 説明 | 実装 |
|-----|------|------|
| **State パラメータ** | CSRF 攻撃の防止 | 32 バイトのランダム値、1回使用 |
| **PKCE** | 認可コード傍受攻撃の防止 | S256 方式の code_challenge |
| **ID Token 署名検証** | トークン改ざんの防止 | JWKS による公開鍵検証 |
| **Nonce 検証** | リプレイ攻撃の防止 | ID Token 内の nonce と照合 |
| **State 有効期限** | 古いセッションの無効化 | 10 分で期限切れ |

### クライアント ID 検証

```kotlin
// ネイティブ SDK からのトークンを検証する際
// 正しいクライアント ID で発行されたか確認
fun validateAudience(claims: OIDCIdTokenClaims, provider: OIDCProvider): Boolean {
    val validAudiences = when (provider) {
        OIDCProvider.GOOGLE -> listOf(
            config.google.clientId,
            config.google.iosClientId,
            config.google.androidClientId
        )
        OIDCProvider.APPLE -> listOf(config.apple.clientId)
        else -> listOf()
    }.filterNotNull()

    return claims.audience in validAudiences
}
```

### エラー情報の制限

```kotlin
// OIDC エラーは詳細を隠す
sealed class OIDCError : DomainError() {
    // ユーザーに見せるメッセージ
    data class AuthenticationFailed(
        override val message: String = "Authentication failed"
    ) : OIDCError()

    // 内部ログ用
    companion object {
        fun logDetail(reason: String, details: Map<String, Any>) {
            StructuredLogger.security(
                "OIDC authentication failed",
                "reason" to reason,
                "details" to details
            )
        }
    }
}
```

---

## API エンドポイント一覧

| メソッド | パス | 認証 | 説明 |
|---------|------|------|------|
| POST | `/auth/oidc/{provider}/init` | 不要 | 認証 URL 取得 |
| POST | `/auth/oidc/{provider}/callback` | 不要 | コールバック処理 |
| POST | `/auth/oidc/{provider}/token` | 不要 | ネイティブトークン交換 |
| POST | `/auth/oidc/{provider}/link` | 必要 | アカウントリンク |
| DELETE | `/auth/oidc/{provider}` | 必要 | リンク解除 |
| GET | `/auth/oidc/accounts` | 必要 | リンク済み一覧 |

### curl 例

```bash
# 1. 認証 URL 取得
curl -X POST http://localhost:8080/api/v1/auth/oidc/google/init \
  -H "Content-Type: application/json" \
  -d '{
    "codeChallenge": "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
    "redirectUri": "myapp://callback"
  }'

# レスポンス
{
  "authUrl": "https://accounts.google.com/o/oauth2/v2/auth?...",
  "state": "abc123..."
}

# 2. コールバック処理
curl -X POST http://localhost:8080/api/v1/auth/oidc/google/callback \
  -H "Content-Type: application/json" \
  -d '{
    "code": "4/0AY0e-g...",
    "state": "abc123...",
    "codeVerifier": "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"
  }'

# 3. ネイティブ SDK トークン交換
curl -X POST http://localhost:8080/api/v1/auth/oidc/google/token \
  -H "Content-Type: application/json" \
  -d '{
    "idToken": "eyJhbGciOiJSUzI1NiIs..."
  }'

# レスポンス
{
  "accessToken": "eyJhbGciOiJIUzI1NiIs...",
  "refreshToken": "550e8400-e29b-41d4-a716-446655440000",
  "user": {
    "id": "user-001",
    "email": "user@example.com",
    "displayName": "John Doe"
  },
  "isNewUser": true
}
```

---

## トラブルシューティング

### よくあるエラー

#### "Invalid state" エラー

**原因**: State パラメータが一致しない、または期限切れ

**対策**:
- State の有効期限を確認（デフォルト 10 分）
- クライアントが state を正しく保持しているか確認
- リダイレクト中に state が変更されていないか確認

#### "Invalid ID Token" エラー

**原因**: ID Token の署名検証に失敗

**対策**:
- JWKS の取得が成功しているか確認
- トークンの発行者（issuer）が正しいか確認
- トークンの有効期限が切れていないか確認
- audience が正しいクライアント ID か確認

#### Apple "invalid_client" エラー

**原因**: Client Secret の生成に失敗

**対策**:
- Private Key が正しい形式か確認
- Team ID、Key ID が正しいか確認
- Client Secret JWT の有効期限（最大 6 ヶ月）を確認

### デバッグ方法

```kotlin
// ID Token のペイロードを確認
fun debugIdToken(idToken: String) {
    val parts = idToken.split(".")
    if (parts.size == 3) {
        val payload = String(Base64.getUrlDecoder().decode(parts[1]))
        println("ID Token Payload: $payload")
    }
}

// OIDC プロバイダーとの通信ログ
// application.conf
ktor {
    development = true
}

// 詳細なログ出力
HttpClient {
    install(Logging) {
        logger = Logger.DEFAULT
        level = LogLevel.ALL
    }
}
```

---

## 関連ドキュメント

- [認証ガイド](authentication.md) - JWT + Refresh Token パターン
- [セキュリティガイド](security.md) - OWASP Top 10 対応
- [エラーハンドリング](error-handling.md) - 認証エラーの処理
- [DI 設定](dependency-injection.md) - Koin モジュール設定
