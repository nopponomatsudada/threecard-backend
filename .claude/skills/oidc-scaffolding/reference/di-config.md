# OIDC DI 設定リファレンス

OIDC 認証に関する依存性注入（Koin）の設定テンプレートです。

## OIDCModule.kt

```kotlin
package {{package}}.di

import {{package}}.data.oidc.*
import {{package}}.data.repository.OIDCRepositoryImpl
import {{package}}.domain.repository.OIDCRepository
import {{package}}.domain.usecase.oidc.*
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import kotlinx.serialization.json.Json
import org.koin.dsl.module

/**
 * OIDC 認証モジュール
 */
fun oidcModule(environment: ApplicationEnvironment) = module {
    // ========================================
    // HTTP Client
    // ========================================

    single {
        HttpClient(CIO) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                })
            }
            install(Logging) {
                logger = Logger.DEFAULT
                level = if (environment.developmentMode) LogLevel.ALL else LogLevel.NONE
            }
            engine {
                requestTimeout = 30_000
                endpoint {
                    connectTimeout = 10_000
                    keepAliveTime = 5_000
                }
            }
        }
    }

    // ========================================
    // OIDC Clients
    // ========================================

    single {
        val config = environment.config

        // 有効なプロバイダーのクライアントを作成
        buildMap<OIDCProvider, OIDCClient> {
            // Google
            if (config.propertyOrNull("oidc.google.enabled")?.getString()?.toBoolean() == true) {
                val googleConfig = GoogleOIDCConfig(
                    clientId = config.property("oidc.google.clientId").getString(),
                    clientSecret = config.property("oidc.google.clientSecret").getString(),
                    iosClientId = config.propertyOrNull("oidc.google.iosClientId")?.getString(),
                    androidClientId = config.propertyOrNull("oidc.google.androidClientId")?.getString()
                )
                put(OIDCProvider.GOOGLE, GoogleOIDCClient(get(), googleConfig))
            }

            // Apple
            if (config.propertyOrNull("oidc.apple.enabled")?.getString()?.toBoolean() == true) {
                val appleConfig = AppleOIDCConfig(
                    clientId = config.property("oidc.apple.clientId").getString(),
                    teamId = config.property("oidc.apple.teamId").getString(),
                    keyId = config.property("oidc.apple.keyId").getString(),
                    privateKey = config.property("oidc.apple.privateKey").getString()
                )
                put(OIDCProvider.APPLE, AppleOIDCClient(get(), appleConfig))
            }

            // GitHub (オプション)
            if (config.propertyOrNull("oidc.github.enabled")?.getString()?.toBoolean() == true) {
                val githubConfig = GitHubOAuthConfig(
                    clientId = config.property("oidc.github.clientId").getString(),
                    clientSecret = config.property("oidc.github.clientSecret").getString()
                )
                put(OIDCProvider.GITHUB, GitHubOAuthClient(get(), githubConfig))
            }

            // LINE (オプション)
            if (config.propertyOrNull("oidc.line.enabled")?.getString()?.toBoolean() == true) {
                val lineConfig = LineOIDCConfig(
                    clientId = config.property("oidc.line.clientId").getString(),
                    clientSecret = config.property("oidc.line.clientSecret").getString()
                )
                put(OIDCProvider.LINE, LineOIDCClient(get(), lineConfig))
            }
        }
    }

    // ========================================
    // Repository
    // ========================================

    single<OIDCRepository> {
        val stateExpiryMinutes = environment.config
            .propertyOrNull("oidc.stateExpiryMinutes")
            ?.getString()
            ?.toLongOrNull()
            ?: 10

        OIDCRepositoryImpl(
            clients = get(),
            stateExpiryMinutes = stateExpiryMinutes
        )
    }

    // ========================================
    // UseCases
    // ========================================

    single { InitiateOIDCUseCase(get()) }
    single { OIDCCallbackUseCase(get(), get(), get()) }
    single { ExchangeProviderTokenUseCase(get(), get(), get()) }
    single { LinkOIDCProviderUseCase(get(), get()) }
    single { UnlinkOIDCProviderUseCase(get(), get()) }
    single { GetLinkedOIDCAccountsUseCase(get()) }
}
```

## Application.kt への追加

```kotlin
// Application.kt

fun Application.module() {
    // 既存の設定
    configureAuthentication()
    configureSerialization()
    configureRouting()
    configureStatusPages()

    // Koin 設定
    install(Koin) {
        modules(
            appModule(environment),
            authModule(environment),
            oidcModule(environment),  // 追加
            // ... 他のモジュール
        )
    }
}
```

## 設定ファイル (application.conf)

```hocon
# OIDC 設定
oidc {
    # State の有効期限（分）
    stateExpiryMinutes = 10
    stateExpiryMinutes = ${?OIDC_STATE_EXPIRY_MINUTES}

    # Google OIDC
    google {
        enabled = true
        enabled = ${?OIDC_GOOGLE_ENABLED}
        clientId = ${?GOOGLE_CLIENT_ID}
        clientSecret = ${?GOOGLE_CLIENT_SECRET}
        # モバイルアプリ用クライアント ID（ネイティブ SDK 使用時）
        iosClientId = ${?GOOGLE_IOS_CLIENT_ID}
        androidClientId = ${?GOOGLE_ANDROID_CLIENT_ID}
    }

    # Apple OIDC
    apple {
        enabled = true
        enabled = ${?OIDC_APPLE_ENABLED}
        clientId = ${?APPLE_CLIENT_ID}
        teamId = ${?APPLE_TEAM_ID}
        keyId = ${?APPLE_KEY_ID}
        # Private Key（PEM 形式、改行は \n でエスケープ）
        privateKey = ${?APPLE_PRIVATE_KEY}
    }

    # GitHub OAuth（オプション）
    github {
        enabled = false
        enabled = ${?OIDC_GITHUB_ENABLED}
        clientId = ${?GITHUB_CLIENT_ID}
        clientSecret = ${?GITHUB_CLIENT_SECRET}
    }

    # LINE OIDC（オプション）
    line {
        enabled = false
        enabled = ${?OIDC_LINE_ENABLED}
        clientId = ${?LINE_CHANNEL_ID}
        clientSecret = ${?LINE_CHANNEL_SECRET}
    }
}
```

## 環境変数テンプレート (.env.example)

```bash
# OIDC 共通設定
OIDC_STATE_EXPIRY_MINUTES=10

# Google OIDC
OIDC_GOOGLE_ENABLED=true
GOOGLE_CLIENT_ID=your-google-client-id.apps.googleusercontent.com
GOOGLE_CLIENT_SECRET=GOCSPX-your-google-client-secret
GOOGLE_IOS_CLIENT_ID=your-ios-client-id.apps.googleusercontent.com
GOOGLE_ANDROID_CLIENT_ID=your-android-client-id.apps.googleusercontent.com

# Apple OIDC
OIDC_APPLE_ENABLED=true
APPLE_CLIENT_ID=com.example.app.signin
APPLE_TEAM_ID=XXXXXXXXXX
APPLE_KEY_ID=XXXXXXXXXX
# Private Key は改行を \n でエスケープ
APPLE_PRIVATE_KEY="-----BEGIN PRIVATE KEY-----\nMIGTAgEAMBMGByqGSM49AgEGCCqGSM49AwEHBHkwdwIBAQQg...\n-----END PRIVATE KEY-----"

# GitHub OAuth（オプション）
OIDC_GITHUB_ENABLED=false
GITHUB_CLIENT_ID=your-github-client-id
GITHUB_CLIENT_SECRET=your-github-client-secret

# LINE OIDC（オプション）
OIDC_LINE_ENABLED=false
LINE_CHANNEL_ID=your-line-channel-id
LINE_CHANNEL_SECRET=your-line-channel-secret
```

## テスト用モジュール

```kotlin
package {{package}}.di

import {{package}}.data.oidc.OIDCClient
import {{package}}.domain.model.*
import {{package}}.domain.repository.OIDCRepository
import io.mockk.coEvery
import io.mockk.mockk
import org.koin.dsl.module

/**
 * テスト用 OIDC モジュール
 */
fun testOidcModule() = module {
    // モック OIDC クライアント
    single {
        mapOf<OIDCProvider, OIDCClient>(
            OIDCProvider.GOOGLE to mockk<OIDCClient>().apply {
                coEvery { provider } returns OIDCProvider.GOOGLE
                coEvery { buildAuthorizationUrl(any(), any(), any(), any(), any()) } returns
                    "https://accounts.google.com/o/oauth2/v2/auth?..."
            }
        )
    }

    // モック Repository（必要に応じてカスタマイズ）
    single<OIDCRepository> {
        mockk<OIDCRepository>().apply {
            coEvery { createState(any(), any(), any(), any()) } returns OIDCState(
                id = OIDCStateId("state-id"),
                state = "test-state",
                provider = OIDCProvider.GOOGLE,
                codeChallenge = "test-challenge",
                redirectUri = "myapp://callback",
                nonce = null,
                expiresAt = java.time.Instant.now().plusSeconds(600)
            )
        }
    }

    // UseCases
    single { InitiateOIDCUseCase(get()) }
    single { OIDCCallbackUseCase(get(), get(), get()) }
    single { ExchangeProviderTokenUseCase(get(), get(), get()) }
    single { LinkOIDCProviderUseCase(get(), get()) }
    single { UnlinkOIDCProviderUseCase(get(), get()) }
    single { GetLinkedOIDCAccountsUseCase(get()) }
}
```

## 依存関係グラフ

```
┌─────────────────────────────────────────────────────────────────┐
│                        OIDCModule                               │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │                    UseCases                              │   │
│  │  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐   │   │
│  │  │ Initiate     │  │ Callback     │  │ Exchange     │   │   │
│  │  │ OIDCUseCase  │  │ UseCase      │  │ TokenUseCase │   │   │
│  │  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘   │   │
│  │         │                 │                 │           │   │
│  │  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐   │   │
│  │  │ Link         │  │ Unlink       │  │ GetLinked    │   │   │
│  │  │ UseCase      │  │ UseCase      │  │ UseCase      │   │   │
│  │  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘   │   │
│  └─────────┼─────────────────┼─────────────────┼───────────┘   │
│            │                 │                 │               │
│            └─────────────────┼─────────────────┘               │
│                              │                                 │
│                              ▼                                 │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │              OIDCRepository (Interface)                  │   │
│  │                         │                                │   │
│  │              OIDCRepositoryImpl                          │   │
│  └──────────────────────────┬──────────────────────────────┘   │
│                              │                                 │
│            ┌─────────────────┼─────────────────┐               │
│            ▼                 ▼                 ▼               │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐          │
│  │ Google       │  │ Apple        │  │ (Other)      │          │
│  │ OIDCClient   │  │ OIDCClient   │  │ Clients      │          │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘          │
│         │                 │                 │                  │
│         └─────────────────┼─────────────────┘                  │
│                           │                                    │
│                           ▼                                    │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │                    HttpClient                            │   │
│  │                   (Ktor CIO)                             │   │
│  └─────────────────────────────────────────────────────────┘   │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘

外部依存:
  - AuthRepository (AuthModule から)
  - UserRepository (AppModule から)
```
