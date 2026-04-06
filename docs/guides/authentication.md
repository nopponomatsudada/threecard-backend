# 認証ガイド

このドキュメントは、JWT + Refresh Token パターンによる認証システムの実装ガイドを提供します。

## 目次

1. [認証フロー全体像](#認証フロー全体像)
2. [トークン設計](#トークン設計)
3. [実装手順](#実装手順)
4. [コード例](#コード例)
5. [クライアント側の実装](#クライアント側の実装)
6. [セキュリティ考慮事項](#セキュリティ考慮事項)

---

## 認証フロー全体像

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           認証フロー                                         │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ┌──────────┐                                    ┌──────────┐              │
│  │ クライアント │                                    │  サーバー  │              │
│  └─────┬────┘                                    └─────┬────┘              │
│        │                                               │                    │
│        │  1. POST /auth/register                       │                    │
│        │     {email, username, password}               │                    │
│        │──────────────────────────────────────────────►│                    │
│        │                                               │                    │
│        │     {accessToken, refreshToken, user}         │                    │
│        │◄──────────────────────────────────────────────│                    │
│        │                                               │                    │
│        │  2. POST /auth/login                          │                    │
│        │     {email, password}                         │                    │
│        │──────────────────────────────────────────────►│                    │
│        │                                               │                    │
│        │     {accessToken, refreshToken, user}         │                    │
│        │◄──────────────────────────────────────────────│                    │
│        │                                               │                    │
│        │  3. GET /api/v1/protected-resource            │                    │
│        │     Authorization: Bearer {accessToken}       │                    │
│        │──────────────────────────────────────────────►│                    │
│        │                                               │                    │
│        │     {data}                                    │                    │
│        │◄──────────────────────────────────────────────│                    │
│        │                                               │                    │
│        │  4. POST /auth/refresh  (accessToken期限切れ時) │                    │
│        │     {refreshToken}                            │                    │
│        │──────────────────────────────────────────────►│                    │
│        │                                               │                    │
│        │     {新accessToken, 新refreshToken, user}     │                    │
│        │◄──────────────────────────────────────────────│                    │
│        │                                               │                    │
│        │  5. POST /auth/logout                         │                    │
│        │     Authorization: Bearer {accessToken}       │                    │
│        │──────────────────────────────────────────────►│                    │
│        │                                               │                    │
│        │     {success}                                 │                    │
│        │◄──────────────────────────────────────────────│                    │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## トークン設計

### 2種類のトークン

| トークン | 形式 | 有効期限 | 用途 |
|---------|------|---------|------|
| **Access Token** | JWT (署名付き) | 15分 | API リクエストの認証 |
| **Refresh Token** | UUID 文字列 | 30日 | Access Token の更新 |

### Access Token (JWT) の構造

```json
{
  "header": {
    "alg": "HS256",
    "typ": "JWT"
  },
  "payload": {
    "aud": "appmaster-api",
    "iss": "appmaster",
    "sub": "user-001",
    "userId": "user-001",
    "email": "user@example.com",
    "iat": 1706000000,
    "exp": 1706000900
  }
}
```

### Refresh Token の保存

```
RefreshTokensTable
├── id: String (UUID)
├── userId: String (FK → Users)
├── token: String (UUID, インデックス付き)
├── expiresAt: Instant
├── createdAt: Instant
└── revokedAt: Instant? (無効化時に設定)
```

### Token Rotation（重要）

Refresh Token 使用時は必ず新しいトークンを発行し、古いトークンを無効化します：

```
1. クライアント: refreshToken を送信
2. サーバー: refreshToken を検証
3. サーバー: 古い refreshToken を無効化 (revokedAt を設定)
4. サーバー: 新しい accessToken + refreshToken を生成
5. クライアント: 新しいトークンを保存
```

これにより、トークンが漏洩した場合でも被害を最小限に抑えられます。

---

## 実装手順

### 1. 依存関係の追加

```kotlin
// build.gradle.kts
dependencies {
    // JWT
    implementation("com.auth0:java-jwt:4.4.0")

    // パスワードハッシュ化
    implementation("at.favre.lib:bcrypt:0.10.2")

    // Ktor 認証
    implementation("io.ktor:ktor-server-auth:$ktor_version")
    implementation("io.ktor:ktor-server-auth-jwt:$ktor_version")
}
```

### 2. 設定ファイル

```hocon
# application.conf
jwt {
    secret = "your-32-character-secret-key-here"
    secret = ${?JWT_SECRET}
    issuer = "appmaster"
    issuer = ${?JWT_ISSUER}
    audience = "appmaster-api"
    audience = ${?JWT_AUDIENCE}
    realm = "AppMaster API"
    accessTokenExpiryMinutes = 15
    refreshTokenExpiryDays = 30
}
```

### 3. 実装順序

```
1. Domain 層
   ├── model/AuthTokens.kt
   └── repository/AuthRepository.kt

2. Data 層
   ├── database/RefreshTokensTable.kt
   └── repository/AuthRepositoryImpl.kt

3. UseCase 層
   ├── usecase/auth/RegisterUseCase.kt
   ├── usecase/auth/LoginUseCase.kt
   ├── usecase/auth/RefreshTokenUseCase.kt
   └── usecase/auth/LogoutUseCase.kt

4. Routes 層
   ├── dto/AuthDto.kt
   └── AuthRoutes.kt

5. Plugin 設定
   └── plugins/Authentication.kt

6. DI 設定
   └── di/AuthModule.kt
```

---

## コード例

### Domain 層

#### AuthTokens.kt

```kotlin
package com.example.domain.model

data class AuthTokens(
    val accessToken: String,
    val refreshToken: String
)
```

#### AuthRepository.kt

```kotlin
package com.example.domain.repository

import com.example.domain.model.AuthTokens
import com.example.domain.model.User
import com.example.domain.model.UserId

interface AuthRepository {
    /**
     * Access Token と Refresh Token を生成
     */
    suspend fun generateTokens(user: User): AuthTokens

    /**
     * Refresh Token を検証し、対応するユーザーIDを返す
     * 無効な場合は null を返す
     */
    suspend fun validateRefreshToken(refreshToken: String): UserId?

    /**
     * Refresh Token を無効化
     */
    suspend fun revokeRefreshToken(refreshToken: String)

    /**
     * ユーザーの全 Refresh Token を無効化（ログアウト時）
     */
    suspend fun revokeAllTokensForUser(userId: UserId)

    /**
     * パスワードをハッシュ化
     */
    fun hashPassword(password: String): String

    /**
     * パスワードを検証
     */
    fun verifyPassword(password: String, hash: String): Boolean
}
```

### Data 層

#### RefreshTokensTable.kt

```kotlin
package com.example.data.database

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp

object RefreshTokensTable : Table("refresh_tokens") {
    val id = varchar("id", 36)
    val userId = varchar("user_id", 36).references(UsersTable.id)
    val token = varchar("token", 36).uniqueIndex()
    val expiresAt = timestamp("expires_at")
    val createdAt = timestamp("created_at")
    val revokedAt = timestamp("revoked_at").nullable()

    override val primaryKey = PrimaryKey(id)
}
```

#### AuthRepositoryImpl.kt

```kotlin
package com.example.data.repository

import at.favre.lib.crypto.bcrypt.BCrypt
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.example.data.database.DatabaseFactory.dbQuery
import com.example.data.database.RefreshTokensTable
import com.example.domain.model.AuthTokens
import com.example.domain.model.User
import com.example.domain.model.UserId
import com.example.domain.repository.AuthRepository
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import java.time.Instant
import java.util.*

class AuthRepositoryImpl(
    private val jwtSecret: String,
    private val jwtIssuer: String,
    private val jwtAudience: String,
    private val accessTokenExpiryMinutes: Long = 15,
    private val refreshTokenExpiryDays: Long = 30
) : AuthRepository {

    private val bcryptHasher = BCrypt.withDefaults()
    private val bcryptVerifier = BCrypt.verifyer()

    override suspend fun generateTokens(user: User): AuthTokens {
        val now = System.currentTimeMillis()
        val accessTokenExpiry = Date(now + accessTokenExpiryMinutes * 60 * 1000)
        val refreshTokenExpiry = Instant.now().plusSeconds(refreshTokenExpiryDays * 24 * 60 * 60)

        // Access Token (JWT) 生成
        val accessToken = JWT.create()
            .withAudience(jwtAudience)
            .withIssuer(jwtIssuer)
            .withSubject(user.id.value)
            .withClaim("userId", user.id.value)
            .withClaim("email", user.email.value)
            .withIssuedAt(Date(now))
            .withExpiresAt(accessTokenExpiry)
            .withJWTId(UUID.randomUUID().toString())
            .sign(Algorithm.HMAC256(jwtSecret))

        // Refresh Token (UUID) 生成
        val refreshToken = UUID.randomUUID().toString()

        // Refresh Token を DB に保存
        dbQuery {
            RefreshTokensTable.insert {
                it[id] = UUID.randomUUID().toString()
                it[userId] = user.id.value
                it[token] = refreshToken
                it[expiresAt] = refreshTokenExpiry
                it[createdAt] = Instant.now()
                it[revokedAt] = null
            }
        }

        return AuthTokens(
            accessToken = accessToken,
            refreshToken = refreshToken
        )
    }

    override suspend fun validateRefreshToken(refreshToken: String): UserId? = dbQuery {
        val now = Instant.now()

        RefreshTokensTable
            .selectAll()
            .where {
                (RefreshTokensTable.token eq refreshToken) and
                (RefreshTokensTable.revokedAt.isNull()) and
                (RefreshTokensTable.expiresAt greater now)
            }
            .singleOrNull()
            ?.let { UserId(it[RefreshTokensTable.userId]) }
    }

    override suspend fun revokeRefreshToken(refreshToken: String): Unit = dbQuery {
        RefreshTokensTable.update({ RefreshTokensTable.token eq refreshToken }) {
            it[revokedAt] = Instant.now()
        }
    }

    override suspend fun revokeAllTokensForUser(userId: UserId): Unit = dbQuery {
        RefreshTokensTable.update({ RefreshTokensTable.userId eq userId.value }) {
            it[revokedAt] = Instant.now()
        }
    }

    override fun hashPassword(password: String): String {
        return bcryptHasher.hashToString(12, password.toCharArray())
    }

    override fun verifyPassword(password: String, hash: String): Boolean {
        return bcryptVerifier.verify(password.toCharArray(), hash).verified
    }
}
```

### UseCase 層

#### RegisterUseCase.kt

```kotlin
package com.example.domain.usecase.auth

import com.example.domain.error.DomainError
import com.example.domain.model.*
import com.example.domain.repository.AuthRepository
import com.example.domain.repository.UserRepository
import java.util.UUID

class RegisterUseCase(
    private val userRepository: UserRepository,
    private val authRepository: AuthRepository
) {
    data class Input(
        val email: Email,
        val username: Username,
        val password: String,
        val displayName: String? = null
    )

    data class Output(
        val accessToken: String,
        val refreshToken: String,
        val user: User
    )

    suspend operator fun invoke(input: Input): Output {
        // パスワード検証
        if (input.password.length < 8) {
            throw DomainError.Validation.passwordTooShort()
        }

        // メールアドレス重複チェック
        if (userRepository.findByEmail(input.email.value) != null) {
            throw DomainError.Conflict.emailExists()
        }

        // ユーザー名重複チェック
        if (userRepository.findByUsername(input.username.value) != null) {
            throw DomainError.Conflict.usernameExists()
        }

        // ユーザー作成
        val user = User(
            id = UserId(UUID.randomUUID().toString()),
            email = input.email,
            username = input.username,
            displayName = input.displayName ?: input.username.value,
            passwordHash = authRepository.hashPassword(input.password)
        )

        val savedUser = userRepository.save(user)
        val tokens = authRepository.generateTokens(savedUser)

        return Output(
            accessToken = tokens.accessToken,
            refreshToken = tokens.refreshToken,
            user = savedUser
        )
    }
}
```

#### LoginUseCase.kt

```kotlin
package com.example.domain.usecase.auth

import com.example.domain.error.DomainError
import com.example.domain.model.User
import com.example.domain.repository.AuthRepository
import com.example.domain.repository.UserRepository

class LoginUseCase(
    private val userRepository: UserRepository,
    private val authRepository: AuthRepository
) {
    data class Input(
        val email: String,
        val password: String
    )

    data class Output(
        val accessToken: String,
        val refreshToken: String,
        val user: User
    )

    suspend operator fun invoke(input: Input): Output {
        // ユーザー検索
        val user = userRepository.findByEmail(input.email)
            ?: throw DomainError.Unauthorized.invalidCredentials()

        // パスワード検証
        if (!authRepository.verifyPassword(input.password, user.passwordHash)) {
            throw DomainError.Unauthorized.invalidCredentials()
        }

        // トークン生成
        val tokens = authRepository.generateTokens(user)

        return Output(
            accessToken = tokens.accessToken,
            refreshToken = tokens.refreshToken,
            user = user
        )
    }
}
```

#### RefreshTokenUseCase.kt

```kotlin
package com.example.domain.usecase.auth

import com.example.domain.error.DomainError
import com.example.domain.model.User
import com.example.domain.repository.AuthRepository
import com.example.domain.repository.UserRepository

class RefreshTokenUseCase(
    private val userRepository: UserRepository,
    private val authRepository: AuthRepository
) {
    data class Output(
        val accessToken: String,
        val refreshToken: String,
        val user: User
    )

    suspend operator fun invoke(refreshToken: String): Output {
        // Refresh Token 検証
        val userId = authRepository.validateRefreshToken(refreshToken)
            ?: throw DomainError.Unauthorized.invalidToken()

        // ユーザー取得
        val user = userRepository.findById(userId)
            ?: throw DomainError.NotFound.user(userId.value)

        // 古いトークンを無効化（Token Rotation）
        authRepository.revokeRefreshToken(refreshToken)

        // 新しいトークンを生成
        val tokens = authRepository.generateTokens(user)

        return Output(
            accessToken = tokens.accessToken,
            refreshToken = tokens.refreshToken,
            user = user
        )
    }
}
```

#### LogoutUseCase.kt

```kotlin
package com.example.domain.usecase.auth

import com.example.domain.model.UserId
import com.example.domain.repository.AuthRepository

class LogoutUseCase(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(userId: UserId) {
        // 該当ユーザーの全 Refresh Token を無効化
        authRepository.revokeAllTokensForUser(userId)
    }
}
```

### Routes 層

#### AuthDto.kt

```kotlin
package com.example.routes.dto

import kotlinx.serialization.Serializable

@Serializable
data class RegisterRequest(
    val email: String,
    val username: String,
    val password: String,
    val displayName: String? = null
)

@Serializable
data class LoginRequest(
    val email: String,
    val password: String
)

@Serializable
data class RefreshTokenRequest(
    val refreshToken: String
)

@Serializable
data class AuthResponse(
    val accessToken: String,
    val refreshToken: String,
    val user: UserDto
)

@Serializable
data class MessageResponse(
    val message: String
)
```

#### AuthRoutes.kt

```kotlin
package com.example.routes

import com.example.domain.model.Email
import com.example.domain.model.UserId
import com.example.domain.model.Username
import com.example.domain.usecase.auth.*
import com.example.routes.dto.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

fun Route.authRoutes() {
    val registerUseCase by inject<RegisterUseCase>()
    val loginUseCase by inject<LoginUseCase>()
    val refreshTokenUseCase by inject<RefreshTokenUseCase>()
    val logoutUseCase by inject<LogoutUseCase>()

    route("/auth") {
        // ユーザー登録
        post("/register") {
            val request = call.receive<RegisterRequest>()

            val input = RegisterUseCase.Input(
                email = Email(request.email),
                username = Username(request.username),
                password = request.password,
                displayName = request.displayName
            )

            val output = registerUseCase(input)

            call.respond(
                HttpStatusCode.Created,
                AuthResponse(
                    accessToken = output.accessToken,
                    refreshToken = output.refreshToken,
                    user = output.user.toDto()
                )
            )
        }

        // ログイン
        post("/login") {
            val request = call.receive<LoginRequest>()

            val input = LoginUseCase.Input(
                email = request.email,
                password = request.password
            )

            val output = loginUseCase(input)

            call.respond(
                AuthResponse(
                    accessToken = output.accessToken,
                    refreshToken = output.refreshToken,
                    user = output.user.toDto()
                )
            )
        }

        // トークン更新
        post("/refresh") {
            val request = call.receive<RefreshTokenRequest>()

            val output = refreshTokenUseCase(request.refreshToken)

            call.respond(
                AuthResponse(
                    accessToken = output.accessToken,
                    refreshToken = output.refreshToken,
                    user = output.user.toDto()
                )
            )
        }

        // ログアウト（認証必須）
        authenticate("jwt") {
            post("/logout") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()
                    ?: throw IllegalStateException("User ID not found in token")

                logoutUseCase(UserId(userId))

                call.respond(MessageResponse("Logged out successfully"))
            }
        }
    }
}
```

### Plugin 設定

#### Authentication.kt

```kotlin
package com.example.plugins

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.example.routes.dto.ErrorResponse
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*

fun Application.configureAuthentication() {
    val jwtSecret = environment.config.property("jwt.secret").getString()
    val jwtIssuer = environment.config.property("jwt.issuer").getString()
    val jwtAudience = environment.config.property("jwt.audience").getString()
    val jwtRealm = environment.config.property("jwt.realm").getString()

    install(Authentication) {
        jwt("jwt") {
            realm = jwtRealm

            verifier(
                JWT.require(Algorithm.HMAC256(jwtSecret))
                    .withIssuer(jwtIssuer)
                    .withAudience(jwtAudience)
                    .build()
            )

            validate { credential ->
                // トークンの audience を検証
                if (credential.payload.audience.contains(jwtAudience)) {
                    JWTPrincipal(credential.payload)
                } else {
                    null
                }
            }

            challenge { _, _ ->
                call.respond(
                    HttpStatusCode.Unauthorized,
                    ErrorResponse(
                        code = "UNAUTHORIZED",
                        message = "Invalid or expired token"
                    )
                )
            }
        }
    }
}
```

### DI 設定

#### AuthModule.kt

```kotlin
package com.example.di

import com.example.data.repository.AuthRepositoryImpl
import com.example.domain.repository.AuthRepository
import com.example.domain.usecase.auth.*
import io.ktor.server.application.*
import org.koin.dsl.module

fun authModule(environment: ApplicationEnvironment) = module {
    // Repository
    single<AuthRepository> {
        AuthRepositoryImpl(
            jwtSecret = environment.config.property("jwt.secret").getString(),
            jwtIssuer = environment.config.property("jwt.issuer").getString(),
            jwtAudience = environment.config.property("jwt.audience").getString(),
            accessTokenExpiryMinutes = environment.config.property("jwt.accessTokenExpiryMinutes").getString().toLong(),
            refreshTokenExpiryDays = environment.config.property("jwt.refreshTokenExpiryDays").getString().toLong()
        )
    }

    // UseCases
    single { RegisterUseCase(get(), get()) }
    single { LoginUseCase(get(), get()) }
    single { RefreshTokenUseCase(get(), get()) }
    single { LogoutUseCase(get()) }
}
```

---

## クライアント側の実装

### Android (Kotlin)

```kotlin
// トークン保存（EncryptedSharedPreferences 推奨）
class TokenStorage(context: Context) {
    private val prefs = EncryptedSharedPreferences.create(
        context,
        "auth_prefs",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    var accessToken: String?
        get() = prefs.getString("access_token", null)
        set(value) = prefs.edit().putString("access_token", value).apply()

    var refreshToken: String?
        get() = prefs.getString("refresh_token", null)
        set(value) = prefs.edit().putString("refresh_token", value).apply()

    fun clear() {
        prefs.edit().clear().apply()
    }
}

// OkHttp Interceptor でトークン自動付与 + リフレッシュ
class AuthInterceptor(
    private val tokenStorage: TokenStorage,
    private val authApi: AuthApi
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
            .apply {
                tokenStorage.accessToken?.let {
                    header("Authorization", "Bearer $it")
                }
            }
            .build()

        val response = chain.proceed(request)

        // 401 の場合、トークンリフレッシュを試行
        if (response.code == 401 && tokenStorage.refreshToken != null) {
            response.close()

            val refreshResponse = runBlocking {
                authApi.refresh(RefreshRequest(tokenStorage.refreshToken!!))
            }

            if (refreshResponse.isSuccessful) {
                val authResponse = refreshResponse.body()!!
                tokenStorage.accessToken = authResponse.accessToken
                tokenStorage.refreshToken = authResponse.refreshToken

                // リトライ
                val newRequest = request.newBuilder()
                    .header("Authorization", "Bearer ${authResponse.accessToken}")
                    .build()
                return chain.proceed(newRequest)
            } else {
                // リフレッシュ失敗 → ログアウト
                tokenStorage.clear()
            }
        }

        return response
    }
}
```

### iOS (Swift)

```swift
// トークン保存（Keychain 推奨）
class TokenStorage {
    private let keychain = Keychain(service: "com.example.app")

    var accessToken: String? {
        get { try? keychain.get("access_token") }
        set {
            if let value = newValue {
                try? keychain.set(value, key: "access_token")
            } else {
                try? keychain.remove("access_token")
            }
        }
    }

    var refreshToken: String? {
        get { try? keychain.get("refresh_token") }
        set {
            if let value = newValue {
                try? keychain.set(value, key: "refresh_token")
            } else {
                try? keychain.remove("refresh_token")
            }
        }
    }

    func clear() {
        try? keychain.removeAll()
    }
}

// URLSession での認証処理
class AuthenticatedURLSession {
    private let tokenStorage: TokenStorage
    private let authService: AuthService

    func request(_ request: URLRequest) async throws -> (Data, URLResponse) {
        var request = request
        if let token = tokenStorage.accessToken {
            request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }

        let (data, response) = try await URLSession.shared.data(for: request)

        if let httpResponse = response as? HTTPURLResponse,
           httpResponse.statusCode == 401,
           let refreshToken = tokenStorage.refreshToken {
            // トークンリフレッシュ
            let authResponse = try await authService.refresh(refreshToken: refreshToken)
            tokenStorage.accessToken = authResponse.accessToken
            tokenStorage.refreshToken = authResponse.refreshToken

            // リトライ
            var retryRequest = request
            retryRequest.setValue("Bearer \(authResponse.accessToken)", forHTTPHeaderField: "Authorization")
            return try await URLSession.shared.data(for: retryRequest)
        }

        return (data, response)
    }
}
```

---

## セキュリティ考慮事項

### 必須事項

| 項目 | 実装 |
|-----|------|
| パスワードハッシュ | BCrypt (コスト係数 12 以上) |
| JWT 署名 | HS256 (開発) / RS256 (本番) |
| Access Token 有効期限 | 15分以下 |
| Refresh Token 有効期限 | 7-30日 |
| Token Rotation | Refresh Token 使用時に必ず新しいトークン発行 |
| HTTPS | 本番環境では必須 |

### チェックリスト

- [ ] JWT シークレットは環境変数から読み込む
- [ ] パスワードは BCrypt でハッシュ化
- [ ] Refresh Token は DB に保存
- [ ] Token Rotation を実装
- [ ] ログアウト時に全トークンを無効化
- [ ] 本番環境では HTTPS を強制

### よくある脆弱性と対策

| 脆弱性 | 対策 |
|-------|------|
| Token 漏洩 | 短い有効期限 + Token Rotation |
| ブルートフォース | レート制限（5回/分など） |
| XSS によるトークン窃取 | HttpOnly Cookie または EncryptedStorage |
| CSRF | SameSite Cookie + CSRF Token |

---

## API エンドポイント一覧

| メソッド | パス | 認証 | 説明 |
|---------|------|------|------|
| POST | /auth/register | 不要 | ユーザー登録 |
| POST | /auth/login | 不要 | ログイン |
| POST | /auth/refresh | 不要 | トークン更新 |
| POST | /auth/logout | 必要 | ログアウト |

### curl 例

```bash
# 登録
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email": "user@example.com", "username": "testuser", "password": "password123"}'

# ログイン
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email": "user@example.com", "password": "password123"}'

# トークン更新
curl -X POST http://localhost:8080/api/v1/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{"refreshToken": "your-refresh-token"}'

# ログアウト
curl -X POST http://localhost:8080/api/v1/auth/logout \
  -H "Authorization: Bearer your-access-token"
```

---

## OIDC (OpenID Connect) 認証

ソーシャルログイン（Google、Apple など）を使用した認証も利用可能です。

### 概要

OIDC は OAuth 2.0 の上に構築された認証レイヤーで、外部プロバイダーを使用したユーザー認証を可能にします。

| プロバイダー | プロトコル | 状態 |
|------------|----------|------|
| Google | OIDC | 優先サポート |
| Apple | OIDC | 優先サポート |
| GitHub | OAuth 2.0 | オプション |
| LINE | OIDC | オプション |

### 認証フローの選択

| フロー | 用途 |
|-------|------|
| **PKCE フロー** | Web ブラウザ経由の認証 |
| **ネイティブ SDK** | Google Sign-In、Sign in with Apple SDK を使用 |

### API エンドポイント

| メソッド | パス | 認証 | 説明 |
|---------|------|------|------|
| POST | `/auth/oidc/{provider}/init` | 不要 | 認証 URL 取得 |
| POST | `/auth/oidc/{provider}/callback` | 不要 | コールバック処理 |
| POST | `/auth/oidc/{provider}/token` | 不要 | ネイティブトークン交換 |
| POST | `/auth/oidc/{provider}/link` | 必要 | アカウントリンク |
| DELETE | `/auth/oidc/{provider}` | 必要 | リンク解除 |
| GET | `/auth/oidc/accounts` | 必要 | リンク済み一覧 |

### 基本的な使用例

```bash
# 1. 認証 URL 取得
curl -X POST http://localhost:8080/api/v1/auth/oidc/google/init \
  -H "Content-Type: application/json" \
  -d '{"codeChallenge": "...", "redirectUri": "myapp://callback"}'

# 2. コールバック処理
curl -X POST http://localhost:8080/api/v1/auth/oidc/google/callback \
  -H "Content-Type: application/json" \
  -d '{"code": "...", "state": "...", "codeVerifier": "..."}'

# 3. ネイティブ SDK トークン交換
curl -X POST http://localhost:8080/api/v1/auth/oidc/google/token \
  -H "Content-Type: application/json" \
  -d '{"idToken": "..."}'
```

### 詳細ドキュメント

OIDC 認証の詳細な実装ガイドは [OIDC 認証ガイド](oidc-authentication.md) を参照してください。

---

## 関連ドキュメント

- [OIDC 認証ガイド](oidc-authentication.md) - OIDC/ソーシャルログインの詳細
- [セキュリティガイド](security.md) - OWASP Top 10 対応、暗号化、HTTP セキュリティ
- [エラーハンドリング](error-handling.md) - 認証エラーの処理
- [DI 設定](dependency-injection.md) - Koin モジュール設定
