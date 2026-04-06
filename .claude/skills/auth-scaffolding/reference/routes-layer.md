# Auth Routes 層リファレンス

認証に関する Routes 層のコードテンプレートです。

## 必須インポート

```kotlin
// Ktor 2.x / 3.x では以下のインポートが必須
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.http.*
```

## AuthDto.kt

```kotlin
package {{package}}.routes.dto

import kotlinx.serialization.Serializable

// ===== リクエスト =====

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
data class ChangePasswordRequest(
    val currentPassword: String,
    val newPassword: String
)

// ===== レスポンス =====

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

## AuthRoutes.kt

```kotlin
package {{package}}.routes

import {{package}}.domain.model.Email
import {{package}}.domain.model.UserId
import {{package}}.domain.model.Username
import {{package}}.domain.usecase.auth.*
import {{package}}.routes.dto.*
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
        // ===== 認証不要 =====

        /**
         * ユーザー登録
         * POST /api/v1/auth/register
         */
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

        /**
         * ログイン
         * POST /api/v1/auth/login
         */
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

        /**
         * トークン更新
         * POST /api/v1/auth/refresh
         */
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

        // ===== 認証必要 =====

        authenticate("jwt") {
            /**
             * ログアウト
             * POST /api/v1/auth/logout
             */
            post("/logout") {
                val userId = call.currentUserId()
                logoutUseCase(userId)
                call.respond(MessageResponse("Logged out successfully"))
            }

            /**
             * 現在のユーザー情報
             * GET /api/v1/auth/me
             */
            get("/me") {
                val userId = call.currentUserId()
                val getCurrentUserUseCase by inject<GetCurrentUserUseCase>()
                val user = getCurrentUserUseCase(userId)
                call.respond(user.toDto())
            }
        }
    }
}

/**
 * JWT から現在のユーザーIDを取得
 */
fun ApplicationCall.currentUserId(): UserId {
    val principal = principal<JWTPrincipal>()
        ?: throw IllegalStateException("JWT principal not found")

    val userId = principal.payload.getClaim("userId").asString()
        ?: throw IllegalStateException("userId claim not found")

    return UserId(userId)
}

/**
 * JWT から現在のユーザーIDを取得（nullable版）
 */
fun ApplicationCall.currentUserIdOrNull(): UserId? {
    return principal<JWTPrincipal>()
        ?.payload
        ?.getClaim("userId")
        ?.asString()
        ?.let { UserId(it) }
}
```

## Authentication.kt (Plugin)

```kotlin
package {{package}}.plugins

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import {{package}}.routes.dto.ErrorResponse
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
                // audience を検証
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

## Routing.kt への追加

```kotlin
package {{package}}.plugins

import {{package}}.routes.*
import io.ktor.server.application.*
import io.ktor.server.routing.*

fun Application.configureRouting() {
    routing {
        route("/api/v1") {
            authRoutes()      // ← 追加
            userRoutes()
            // ... 他のルート
        }
    }
}
```

## エラーハンドリング

### ErrorHandler.kt

```kotlin
package {{package}}.plugins

import {{package}}.domain.error.DomainError
import {{package}}.routes.dto.ErrorResponse
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*

fun Application.configureErrorHandling() {
    install(StatusPages) {
        // 認証エラー
        exception<DomainError.Unauthorized> { call, cause ->
            call.respond(
                HttpStatusCode.Unauthorized,
                ErrorResponse(
                    code = "UNAUTHORIZED",
                    message = cause.message ?: "Authentication failed"
                )
            )
        }

        // 競合エラー（重複など）
        exception<DomainError.Conflict> { call, cause ->
            call.respond(
                HttpStatusCode.Conflict,
                ErrorResponse(
                    code = "CONFLICT",
                    message = cause.message ?: "Resource conflict"
                )
            )
        }

        // バリデーションエラー
        exception<DomainError.Validation> { call, cause ->
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse(
                    code = "VALIDATION_ERROR",
                    message = cause.message ?: "Validation failed"
                )
            )
        }

        // Not Found
        exception<DomainError.NotFound> { call, cause ->
            call.respond(
                HttpStatusCode.NotFound,
                ErrorResponse(
                    code = "NOT_FOUND",
                    message = cause.message ?: "Resource not found"
                )
            )
        }

        // その他の例外
        exception<Exception> { call, cause ->
            call.application.log.error("Unhandled exception", cause)
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponse(
                    code = "INTERNAL_ERROR",
                    message = "An unexpected error occurred"
                )
            )
        }
    }
}
```

## レート制限 (オプション)

```kotlin
package {{package}}.plugins

import io.ktor.server.application.*
import io.ktor.server.plugins.ratelimit.*
import kotlin.time.Duration.Companion.minutes

fun Application.configureRateLimiting() {
    install(RateLimit) {
        // 認証エンドポイント用（より厳しい制限）
        register(RateLimitName("auth")) {
            rateLimiter(limit = 5, refillPeriod = 1.minutes)
        }

        // 一般 API 用
        register(RateLimitName("api")) {
            rateLimiter(limit = 60, refillPeriod = 1.minutes)
        }
    }
}

// AuthRoutes.kt での使用
route("/auth") {
    rateLimit(RateLimitName("auth")) {
        post("/login") { /* ... */ }
        post("/register") { /* ... */ }
    }
}
```

## API エンドポイント一覧

| メソッド | パス | 認証 | 説明 |
|---------|------|------|------|
| POST | /api/v1/auth/register | 不要 | ユーザー登録 |
| POST | /api/v1/auth/login | 不要 | ログイン |
| POST | /api/v1/auth/refresh | 不要 | トークン更新 |
| POST | /api/v1/auth/logout | 必要 | ログアウト |
| GET | /api/v1/auth/me | 必要 | 現在のユーザー取得 |
