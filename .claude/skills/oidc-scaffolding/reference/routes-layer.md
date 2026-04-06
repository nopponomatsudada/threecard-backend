# OIDC Routes 層リファレンス

OIDC 認証に関する Routes 層のコードテンプレートです。

## OIDCDto.kt

```kotlin
package {{package}}.routes.dto

import kotlinx.serialization.Serializable

// ========================================
// Request DTOs
// ========================================

/**
 * OIDC 認証開始リクエスト
 */
@Serializable
data class OIDCInitRequest(
    /** PKCE の code_challenge (S256) */
    val codeChallenge: String,
    /** コールバック後のリダイレクト先 */
    val redirectUri: String,
    /** リプレイ攻撃防止用の nonce（オプション） */
    val nonce: String? = null
)

/**
 * OIDC コールバックリクエスト
 */
@Serializable
data class OIDCCallbackRequest(
    /** プロバイダーから受け取った認可コード */
    val code: String,
    /** CSRF 対策の state パラメータ */
    val state: String,
    /** PKCE の code_verifier */
    val codeVerifier: String
)

/**
 * ネイティブ SDK トークン交換リクエスト
 */
@Serializable
data class OIDCTokenExchangeRequest(
    /** ネイティブ SDK から取得した ID Token */
    val idToken: String,
    /** リプレイ攻撃防止用の nonce（オプション） */
    val nonce: String? = null
)

/**
 * アカウントリンクリクエスト
 */
@Serializable
data class OIDCLinkRequest(
    /** プロバイダーから受け取った認可コード */
    val code: String,
    /** CSRF 対策の state パラメータ */
    val state: String,
    /** PKCE の code_verifier */
    val codeVerifier: String
)

// ========================================
// Response DTOs
// ========================================

/**
 * OIDC 認証開始レスポンス
 */
@Serializable
data class OIDCInitResponse(
    /** プロバイダーの認証 URL */
    val authUrl: String,
    /** CSRF 対策の state（クライアントで保持） */
    val state: String
)

/**
 * 認証レスポンス（OIDC 用拡張）
 */
@Serializable
data class OIDCAuthResponse(
    val accessToken: String,
    val refreshToken: String,
    val user: UserDto,
    /** 新規ユーザーとして作成されたか */
    val isNewUser: Boolean
)

/**
 * OIDC アカウント情報
 */
@Serializable
data class OIDCAccountResponse(
    /** プロバイダー ID (google, apple, etc.) */
    val provider: String,
    /** プロバイダーから取得したメールアドレス */
    val email: String?,
    /** リンク日時 (ISO 8601) */
    val linkedAt: String
)

/**
 * リンク済みアカウント一覧レスポンス
 */
@Serializable
data class LinkedAccountsResponse(
    val accounts: List<OIDCAccountResponse>,
    /** パスワード認証が設定されているか */
    val hasPasswordAuth: Boolean
)
```

## OIDCRoutes.kt

```kotlin
package {{package}}.routes

import {{package}}.domain.model.OIDCProvider
import {{package}}.domain.model.UserId
import {{package}}.domain.usecase.oidc.*
import {{package}}.routes.dto.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

/**
 * OIDC 認証ルート
 */
fun Route.oidcRoutes() {
    val initiateUseCase by inject<InitiateOIDCUseCase>()
    val callbackUseCase by inject<OIDCCallbackUseCase>()
    val exchangeTokenUseCase by inject<ExchangeProviderTokenUseCase>()
    val linkUseCase by inject<LinkOIDCProviderUseCase>()
    val unlinkUseCase by inject<UnlinkOIDCProviderUseCase>()
    val getLinkedAccountsUseCase by inject<GetLinkedOIDCAccountsUseCase>()

    route("/auth/oidc") {
        // ========================================
        // 認証不要エンドポイント
        // ========================================

        /**
         * 認証 URL 取得
         *
         * POST /auth/oidc/{provider}/init
         *
         * クライアントは以下の手順で使用:
         * 1. code_verifier を生成（43-128文字のランダム文字列）
         * 2. code_challenge = BASE64URL(SHA256(code_verifier)) を計算
         * 3. このエンドポイントを呼び出し
         * 4. 返却された authUrl をブラウザで開く
         * 5. ユーザー認証後、コールバックで code と state を受け取る
         */
        post("/{provider}/init") {
            val provider = call.parameters["provider"]
                ?.let { OIDCProvider.fromId(it) }
                ?: return@post call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("INVALID_PROVIDER", "Invalid or unsupported provider")
                )

            val request = call.receive<OIDCInitRequest>()

            val output = initiateUseCase(
                InitiateOIDCUseCase.Input(
                    provider = provider,
                    codeChallenge = request.codeChallenge,
                    redirectUri = request.redirectUri,
                    nonce = request.nonce
                )
            )

            call.respond(
                HttpStatusCode.OK,
                OIDCInitResponse(
                    authUrl = output.authUrl,
                    state = output.state
                )
            )
        }

        /**
         * コールバック処理
         *
         * POST /auth/oidc/{provider}/callback
         *
         * プロバイダーからリダイレクトされた後、クライアントがこのエンドポイントを呼び出す
         * code と state を検証し、ユーザー認証を行う
         */
        post("/{provider}/callback") {
            val provider = call.parameters["provider"]
                ?.let { OIDCProvider.fromId(it) }
                ?: return@post call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("INVALID_PROVIDER", "Invalid or unsupported provider")
                )

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
                OIDCAuthResponse(
                    accessToken = output.accessToken,
                    refreshToken = output.refreshToken,
                    user = output.user.toDto(),
                    isNewUser = output.isNewUser
                )
            )
        }

        /**
         * ネイティブ SDK トークン交換
         *
         * POST /auth/oidc/{provider}/token
         *
         * Google Sign-In SDK や Sign in with Apple SDK を使用する場合、
         * クライアントは ID Token を直接取得できる。
         * このエンドポイントでその ID Token を検証し、バックエンドの JWT を発行する。
         */
        post("/{provider}/token") {
            val provider = call.parameters["provider"]
                ?.let { OIDCProvider.fromId(it) }
                ?: return@post call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("INVALID_PROVIDER", "Invalid or unsupported provider")
                )

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
                OIDCAuthResponse(
                    accessToken = output.accessToken,
                    refreshToken = output.refreshToken,
                    user = output.user.toDto(),
                    isNewUser = output.isNewUser
                )
            )
        }

        // ========================================
        // 認証必須エンドポイント
        // ========================================

        authenticate("jwt") {
            /**
             * アカウントリンク
             *
             * POST /auth/oidc/{provider}/link
             *
             * 既存アカウントに OIDC プロバイダーを追加リンク
             * コールバックと同様のフローだが、新規ユーザー作成は行わない
             */
            post("/{provider}/link") {
                val provider = call.parameters["provider"]
                    ?.let { OIDCProvider.fromId(it) }
                    ?: return@post call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("INVALID_PROVIDER", "Invalid or unsupported provider")
                    )

                val userId = call.principal<JWTPrincipal>()
                    ?.payload?.getClaim("userId")?.asString()
                    ?: return@post call.respond(
                        HttpStatusCode.Unauthorized,
                        ErrorResponse("UNAUTHORIZED", "User not found in token")
                    )

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

            /**
             * リンク解除
             *
             * DELETE /auth/oidc/{provider}
             *
             * OIDC プロバイダーとのリンクを解除
             * 注意: 最後の認証方法を削除することはできない
             */
            delete("/{provider}") {
                val provider = call.parameters["provider"]
                    ?.let { OIDCProvider.fromId(it) }
                    ?: return@delete call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("INVALID_PROVIDER", "Invalid or unsupported provider")
                    )

                val userId = call.principal<JWTPrincipal>()
                    ?.payload?.getClaim("userId")?.asString()
                    ?: return@delete call.respond(
                        HttpStatusCode.Unauthorized,
                        ErrorResponse("UNAUTHORIZED", "User not found in token")
                    )

                unlinkUseCase(
                    UnlinkOIDCProviderUseCase.Input(
                        userId = UserId(userId),
                        provider = provider
                    )
                )

                call.respond(HttpStatusCode.NoContent)
            }

            /**
             * リンク済みアカウント一覧
             *
             * GET /auth/oidc/accounts
             *
             * 現在のユーザーにリンクされている OIDC アカウントの一覧を取得
             */
            get("/accounts") {
                val userId = call.principal<JWTPrincipal>()
                    ?.payload?.getClaim("userId")?.asString()
                    ?: return@get call.respond(
                        HttpStatusCode.Unauthorized,
                        ErrorResponse("UNAUTHORIZED", "User not found in token")
                    )

                val accounts = getLinkedAccountsUseCase(UserId(userId))

                call.respond(
                    HttpStatusCode.OK,
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

## Routing.kt への追加

```kotlin
// plugins/Routing.kt

fun Application.configureRouting() {
    routing {
        route("/api/v1") {
            // 既存のルート
            authRoutes()
            userRoutes()

            // OIDC ルートを追加
            oidcRoutes()
        }
    }
}
```

## エラーハンドリングへの追加

```kotlin
// plugins/StatusPages.kt

fun Application.configureStatusPages() {
    install(StatusPages) {
        // ... 既存の例外ハンドラ ...

        // OIDC エラー
        exception<DomainError.OIDC.InvalidState> { call, cause ->
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse("INVALID_STATE", cause.message ?: "Invalid or expired state")
            )
        }

        exception<DomainError.OIDC.InvalidIdToken> { call, cause ->
            call.respond(
                HttpStatusCode.Unauthorized,
                ErrorResponse("INVALID_ID_TOKEN", cause.message ?: "Invalid ID token")
            )
        }

        exception<DomainError.OIDC.ProviderNotEnabled> { call, cause ->
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse("PROVIDER_NOT_ENABLED", cause.message ?: "Provider not enabled")
            )
        }

        exception<DomainError.OIDC.AlreadyLinked> { call, cause ->
            call.respond(
                HttpStatusCode.Conflict,
                ErrorResponse("ALREADY_LINKED", cause.message ?: "Account already linked")
            )
        }

        exception<DomainError.OIDC.NotLinked> { call, cause ->
            call.respond(
                HttpStatusCode.NotFound,
                ErrorResponse("NOT_LINKED", cause.message ?: "Account not linked")
            )
        }

        exception<DomainError.OIDC.CannotUnlink> { call, cause ->
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse("CANNOT_UNLINK", cause.message ?: "Cannot unlink the only authentication method")
            )
        }

        exception<DomainError.OIDC.ProviderError> { call, cause ->
            // プロバイダーエラーの詳細は内部ログのみ
            call.application.log.error("OIDC provider error: ${cause.reason}")
            call.respond(
                HttpStatusCode.BadGateway,
                ErrorResponse("PROVIDER_ERROR", "Authentication provider error")
            )
        }
    }
}
```

## API 仕様 (OpenAPI)

```yaml
# api-spec/openapi.yml に追加

paths:
  /auth/oidc/{provider}/init:
    post:
      summary: OIDC 認証開始
      description: |
        OIDC プロバイダーの認証 URL を取得します。
        クライアントは PKCE の code_challenge を生成して送信します。
      tags:
        - OIDC
      parameters:
        - name: provider
          in: path
          required: true
          schema:
            type: string
            enum: [google, apple, github, line]
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/OIDCInitRequest'
      responses:
        '200':
          description: 認証 URL 取得成功
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/OIDCInitResponse'
        '400':
          description: 無効なプロバイダーまたはリクエスト

  /auth/oidc/{provider}/callback:
    post:
      summary: OIDC コールバック処理
      description: |
        プロバイダーから受け取った認可コードを検証し、
        ユーザー認証を行います。
      tags:
        - OIDC
      parameters:
        - name: provider
          in: path
          required: true
          schema:
            type: string
            enum: [google, apple, github, line]
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/OIDCCallbackRequest'
      responses:
        '200':
          description: 認証成功
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/OIDCAuthResponse'
        '400':
          description: 無効な state または code

  /auth/oidc/{provider}/token:
    post:
      summary: ネイティブ SDK トークン交換
      description: |
        ネイティブ SDK から取得した ID Token を検証し、
        バックエンドの JWT を発行します。
      tags:
        - OIDC
      parameters:
        - name: provider
          in: path
          required: true
          schema:
            type: string
            enum: [google, apple]
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/OIDCTokenExchangeRequest'
      responses:
        '200':
          description: トークン交換成功
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/OIDCAuthResponse'
        '401':
          description: 無効な ID Token

components:
  schemas:
    OIDCInitRequest:
      type: object
      required:
        - codeChallenge
        - redirectUri
      properties:
        codeChallenge:
          type: string
          description: PKCE の code_challenge (S256)
        redirectUri:
          type: string
          description: コールバック後のリダイレクト先
        nonce:
          type: string
          description: リプレイ攻撃防止用の nonce

    OIDCInitResponse:
      type: object
      properties:
        authUrl:
          type: string
          description: プロバイダーの認証 URL
        state:
          type: string
          description: CSRF 対策の state

    OIDCCallbackRequest:
      type: object
      required:
        - code
        - state
        - codeVerifier
      properties:
        code:
          type: string
          description: プロバイダーから受け取った認可コード
        state:
          type: string
          description: CSRF 対策の state
        codeVerifier:
          type: string
          description: PKCE の code_verifier

    OIDCTokenExchangeRequest:
      type: object
      required:
        - idToken
      properties:
        idToken:
          type: string
          description: ネイティブ SDK から取得した ID Token
        nonce:
          type: string
          description: リプレイ攻撃防止用の nonce

    OIDCAuthResponse:
      type: object
      properties:
        accessToken:
          type: string
        refreshToken:
          type: string
        user:
          $ref: '#/components/schemas/User'
        isNewUser:
          type: boolean
          description: 新規ユーザーとして作成されたか

    OIDCAccountResponse:
      type: object
      properties:
        provider:
          type: string
          enum: [google, apple, github, line]
        email:
          type: string
          nullable: true
        linkedAt:
          type: string
          format: date-time
```
