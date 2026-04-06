# Routes 層ガイド

## 概要

Routes 層は HTTP リクエストを受け取り、Domain 層の UseCase を呼び出し、結果を HTTP レスポンスとして返します。

## 必須インポート（Ktor 2.x / 3.x）

> **重要**: Ktor 2.x 以降では、Route ハンドラ内で `call` を使用するために以下のインポートが必須です。

```kotlin
// routes/UserRoutes.kt
package com.example.routes

// 必須インポート
import io.ktor.server.application.*   // ← call を使うために必要
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.http.*

// 認証が必要な場合
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*

// DI（Koin）
import org.koin.ktor.ext.inject
```

### よくあるエラー

```
e: Unresolved reference: call
```

**原因**: `io.ktor.server.application.*` のインポートが不足しています。

**解決策**: 上記の必須インポートを追加してください。

## 構成要素

### 1. DTO (Data Transfer Object)

#### Request DTO

```kotlin
// routes/dto/request/CreateUserRequest.kt
@Serializable
data class CreateUserRequest(
    val email: String,
    val name: String
) {
    fun validate(): List<String> {
        val errors = mutableListOf<String>()
        if (email.isBlank()) errors.add("email is required")
        if (name.isBlank()) errors.add("name is required")
        return errors
    }
}

// routes/dto/request/UpdateUserRequest.kt
@Serializable
data class UpdateUserRequest(
    val name: String? = null,
    val status: String? = null
)
```

#### Response DTO

```kotlin
// routes/dto/response/UserResponse.kt
@Serializable
data class UserResponse(
    val id: String,
    val email: String,
    val name: String,
    val status: String,
    val createdAt: String,
    val updatedAt: String
) {
    companion object {
        fun from(user: User): UserResponse = UserResponse(
            id = user.id.value,
            email = user.email.value,
            name = user.name.value,
            status = user.status.name,
            createdAt = user.createdAt.toString(),
            updatedAt = user.updatedAt.toString()
        )
    }
}

// routes/dto/response/ApiResponse.kt
@Serializable
data class ApiResponse<T>(
    val data: T,
    val meta: Meta = Meta()
)

@Serializable
data class Meta(
    val timestamp: String = Instant.now().toString()
)

// routes/dto/response/ErrorResponse.kt
@Serializable
data class ErrorResponse(
    val error: ErrorDetail
)

@Serializable
data class ErrorDetail(
    val code: String,
    val message: String,
    val details: Map<String, String>? = null
)
```

### 2. Route 定義

```kotlin
// routes/UserRoutes.kt
fun Route.userRoutes() {
    val getUserUseCase by inject<GetUserUseCase>()
    val createUserUseCase by inject<CreateUserUseCase>()
    val updateUserUseCase by inject<UpdateUserUseCase>()
    val deleteUserUseCase by inject<DeleteUserUseCase>()
    val listUsersUseCase by inject<ListUsersUseCase>()

    route("/api/v1/users") {
        // GET /api/v1/users
        get {
            val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 20
            val offset = call.request.queryParameters["offset"]?.toIntOrNull() ?: 0

            val users = listUsersUseCase(limit, offset)
            call.respond(ApiResponse(data = users.map { UserResponse.from(it) }))
        }

        // GET /api/v1/users/{id}
        get("/{id}") {
            val id = call.parameters["id"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, errorResponse("MISSING_ID", "id is required"))

            getUserUseCase(UserId(id))
                .onSuccess { user ->
                    call.respond(ApiResponse(data = UserResponse.from(user)))
                }
                .onFailure { error ->
                    call.respondError(error)
                }
        }

        // POST /api/v1/users
        post {
            val request = call.receive<CreateUserRequest>()

            // バリデーション
            val errors = request.validate()
            if (errors.isNotEmpty()) {
                return@post call.respond(
                    HttpStatusCode.BadRequest,
                    errorResponse("VALIDATION_ERROR", "Validation failed", errors)
                )
            }

            // ValueObject 変換
            val email = Email.create(request.email).getOrElse {
                return@post call.respondError(it)
            }
            val name = UserName.create(request.name).getOrElse {
                return@post call.respondError(it)
            }

            createUserUseCase(email, name)
                .onSuccess { user ->
                    call.respond(HttpStatusCode.Created, ApiResponse(data = UserResponse.from(user)))
                }
                .onFailure { error ->
                    call.respondError(error)
                }
        }

        // PUT /api/v1/users/{id}
        put("/{id}") {
            val id = call.parameters["id"]
                ?: return@put call.respond(HttpStatusCode.BadRequest, errorResponse("MISSING_ID", "id is required"))

            val request = call.receive<UpdateUserRequest>()

            updateUserUseCase(UserId(id), request.name, request.status)
                .onSuccess { user ->
                    call.respond(ApiResponse(data = UserResponse.from(user)))
                }
                .onFailure { error ->
                    call.respondError(error)
                }
        }

        // DELETE /api/v1/users/{id}
        delete("/{id}") {
            val id = call.parameters["id"]
                ?: return@delete call.respond(HttpStatusCode.BadRequest, errorResponse("MISSING_ID", "id is required"))

            deleteUserUseCase(UserId(id))
                .onSuccess {
                    call.respond(HttpStatusCode.NoContent)
                }
                .onFailure { error ->
                    call.respondError(error)
                }
        }
    }
}
```

### 3. 認証付きルート

```kotlin
// routes/AuthRoutes.kt
fun Route.authRoutes() {
    val registerUseCase by inject<RegisterUseCase>()
    val loginUseCase by inject<LoginUseCase>()

    route("/api/v1/auth") {
        // POST /api/v1/auth/register
        post("/register") {
            val request = call.receive<RegisterRequest>()
            // ...
        }

        // POST /api/v1/auth/login
        post("/login") {
            val request = call.receive<LoginRequest>()
            // ...
        }
    }
}

// 認証が必要なルート
fun Route.protectedRoutes() {
    authenticate("jwt") {
        route("/api/v1") {
            userRoutes()
            // 他の認証必須ルート
        }
    }
}
```

## Routing Plugin

```kotlin
// plugins/Routing.kt
fun Application.configureRouting() {
    routing {
        // ヘルスチェック
        get("/health") {
            call.respond(mapOf("status" to "healthy"))
        }

        // 認証不要ルート
        authRoutes()

        // 認証必須ルート
        protectedRoutes()
    }
}
```

## エラーレスポンス

### ヘルパー関数

```kotlin
// routes/ErrorHandling.kt
fun errorResponse(code: String, message: String, details: List<String>? = null): ErrorResponse {
    return ErrorResponse(
        error = ErrorDetail(
            code = code,
            message = message,
            details = details?.mapIndexed { i, msg -> "error_$i" to msg }?.toMap()
        )
    )
}

suspend fun ApplicationCall.respondError(error: Throwable) {
    when (error) {
        is DomainError -> {
            val statusCode = when (error) {
                is DomainError.UserNotFound -> HttpStatusCode.NotFound
                is DomainError.EmailAlreadyExists -> HttpStatusCode.Conflict
                is DomainError.InvalidEmail,
                is DomainError.UserNameTooShort,
                is DomainError.UserNameTooLong -> HttpStatusCode.BadRequest
                else -> HttpStatusCode.BadRequest
            }
            respond(statusCode, ErrorResponse(ErrorDetail(error.code, error.message ?: "Unknown error")))
        }
        else -> {
            respond(
                HttpStatusCode.InternalServerError,
                ErrorResponse(ErrorDetail("INTERNAL_ERROR", "An unexpected error occurred"))
            )
        }
    }
}
```

## 入力バリデーション

### Request DTO でのバリデーション

```kotlin
@Serializable
data class CreateUserRequest(
    val email: String,
    val name: String
) {
    fun validate(): ValidationResult {
        val errors = mutableListOf<ValidationError>()

        if (email.isBlank()) {
            errors.add(ValidationError("email", "Email is required"))
        }
        if (name.isBlank()) {
            errors.add(ValidationError("name", "Name is required"))
        } else if (name.length > 50) {
            errors.add(ValidationError("name", "Name must be 50 characters or less"))
        }

        return if (errors.isEmpty()) {
            ValidationResult.Valid
        } else {
            ValidationResult.Invalid(errors)
        }
    }
}

sealed class ValidationResult {
    object Valid : ValidationResult()
    data class Invalid(val errors: List<ValidationError>) : ValidationResult()
}

data class ValidationError(val field: String, val message: String)
```

## テスト

### API テスト

```kotlin
class UserRoutesTest : FunSpec({
    test("GET /api/v1/users/{id} should return user") {
        testApplication {
            application {
                configureRouting()
                configureSerialization()
                // テスト用 DI 設定
            }

            val response = client.get("/api/v1/users/test-id") {
                header(HttpHeaders.Authorization, "Bearer $testToken")
            }

            response.status shouldBe HttpStatusCode.OK
            val body = response.body<ApiResponse<UserResponse>>()
            body.data.id shouldBe "test-id"
        }
    }

    test("POST /api/v1/users should create user") {
        testApplication {
            application {
                configureRouting()
                configureSerialization()
            }

            val response = client.post("/api/v1/users") {
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                header(HttpHeaders.Authorization, "Bearer $testToken")
                setBody("""{"email": "test@example.com", "name": "Test User"}""")
            }

            response.status shouldBe HttpStatusCode.Created
        }
    }
})
```
