# エラーハンドリングガイド

## 概要

エラーは Domain 層で定義し、Routes 層で HTTP レスポンスに変換します。

## エラー分類

### 1. Domain Error（ビジネスエラー）

ビジネスルール違反を表すエラーです。

```kotlin
// domain/error/DomainError.kt
sealed class DomainError : Exception() {
    // バリデーションエラー
    data class InvalidEmail(val email: String) : DomainError()
    data class UserNameTooShort(val minLength: Int) : DomainError()
    data class UserNameTooLong(val maxLength: Int) : DomainError()
    data class InvalidPassword(val reason: String) : DomainError()

    // ビジネスルールエラー
    data class UserNotFound(val id: UserId) : DomainError()
    data class EmailAlreadyExists(val email: Email) : DomainError()
    data class UserAlreadyActive(val id: UserId) : DomainError()
    data class Unauthorized(val reason: String) : DomainError()
    data class Forbidden(val resource: String) : DomainError()

    // エラーコード
    val code: String
        get() = when (this) {
            is InvalidEmail -> "INVALID_EMAIL"
            is UserNameTooShort -> "USERNAME_TOO_SHORT"
            is UserNameTooLong -> "USERNAME_TOO_LONG"
            is InvalidPassword -> "INVALID_PASSWORD"
            is UserNotFound -> "USER_NOT_FOUND"
            is EmailAlreadyExists -> "EMAIL_ALREADY_EXISTS"
            is UserAlreadyActive -> "USER_ALREADY_ACTIVE"
            is Unauthorized -> "UNAUTHORIZED"
            is Forbidden -> "FORBIDDEN"
        }

    override val message: String
        get() = when (this) {
            is InvalidEmail -> "無効なメールアドレス形式です: $email"
            is UserNameTooShort -> "ユーザー名は${minLength}文字以上必要です"
            is UserNameTooLong -> "ユーザー名は${maxLength}文字以下にしてください"
            is InvalidPassword -> "パスワードが無効です: $reason"
            is UserNotFound -> "ユーザーが見つかりません"
            is EmailAlreadyExists -> "このメールアドレスは既に使用されています"
            is UserAlreadyActive -> "ユーザーは既にアクティブです"
            is Unauthorized -> "認証が必要です: $reason"
            is Forbidden -> "アクセスが拒否されました: $resource"
        }
}
```

### 2. Infrastructure Error（インフラエラー）

データベース接続エラーなど、インフラ層で発生するエラーです。

```kotlin
// data/error/InfrastructureError.kt
sealed class InfrastructureError : Exception() {
    data class DatabaseConnection(val cause: Throwable) : InfrastructureError()
    data class ExternalApiError(val service: String, val statusCode: Int) : InfrastructureError()
    data class Timeout(val operation: String) : InfrastructureError()
}
```

## エラー → HTTP ステータスコードマッピング

```kotlin
// routes/ErrorMapping.kt
fun DomainError.toHttpStatusCode(): HttpStatusCode = when (this) {
    // 400 Bad Request - バリデーションエラー
    is DomainError.InvalidEmail,
    is DomainError.UserNameTooShort,
    is DomainError.UserNameTooLong,
    is DomainError.InvalidPassword -> HttpStatusCode.BadRequest

    // 401 Unauthorized - 認証エラー
    is DomainError.Unauthorized -> HttpStatusCode.Unauthorized

    // 403 Forbidden - 認可エラー
    is DomainError.Forbidden -> HttpStatusCode.Forbidden

    // 404 Not Found - リソースが見つからない
    is DomainError.UserNotFound -> HttpStatusCode.NotFound

    // 409 Conflict - 競合
    is DomainError.EmailAlreadyExists,
    is DomainError.UserAlreadyActive -> HttpStatusCode.Conflict
}
```

## グローバルエラーハンドラー

```kotlin
// plugins/StatusPages.kt
fun Application.configureStatusPages() {
    install(StatusPages) {
        // DomainError のハンドリング
        exception<DomainError> { call, cause ->
            call.respond(
                cause.toHttpStatusCode(),
                ErrorResponse(ErrorDetail(cause.code, cause.message ?: "Unknown error"))
            )
        }

        // InfrastructureError のハンドリング
        exception<InfrastructureError> { call, cause ->
            // ログ出力（詳細は隠す）
            call.application.log.error("Infrastructure error", cause)
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponse(ErrorDetail("INTERNAL_ERROR", "An unexpected error occurred"))
            )
        }

        // バリデーションエラー
        exception<SerializationException> { call, cause ->
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse(ErrorDetail("INVALID_REQUEST_BODY", "Request body is invalid"))
            )
        }

        // 未知のエラー
        exception<Throwable> { call, cause ->
            call.application.log.error("Unexpected error", cause)
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponse(ErrorDetail("INTERNAL_ERROR", "An unexpected error occurred"))
            )
        }

        // 404 Not Found
        status(HttpStatusCode.NotFound) { call, status ->
            call.respond(
                status,
                ErrorResponse(ErrorDetail("NOT_FOUND", "The requested resource was not found"))
            )
        }
    }
}
```

## Result 型によるエラーハンドリング

### UseCase での使用

```kotlin
class CreateUserUseCase(private val repository: UserRepository) {
    suspend operator fun invoke(email: Email, name: UserName): Result<User> {
        // 重複チェック
        repository.findByEmail(email)?.let {
            return Result.failure(DomainError.EmailAlreadyExists(email))
        }

        return try {
            val user = User.create(email, name)
            Result.success(repository.save(user))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
```

### Routes での使用

```kotlin
post("/users") {
    val request = call.receive<CreateUserRequest>()

    // ValueObject 変換（バリデーション含む）
    val email = Email.create(request.email).getOrElse {
        return@post call.respondError(it)
    }
    val name = UserName.create(request.name).getOrElse {
        return@post call.respondError(it)
    }

    // UseCase 呼び出し
    createUserUseCase(email, name)
        .onSuccess { user ->
            call.respond(HttpStatusCode.Created, ApiResponse(data = UserResponse.from(user)))
        }
        .onFailure { error ->
            call.respondError(error)
        }
}
```

## エラーレスポンス形式

### 標準形式

```json
{
  "error": {
    "code": "USER_NOT_FOUND",
    "message": "ユーザーが見つかりません",
    "details": null
  }
}
```

### バリデーションエラー

```json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "入力値が不正です",
    "details": {
      "email": "メールアドレスの形式が不正です",
      "name": "名前は必須です"
    }
  }
}
```

## プラットフォーム間のエラーコード統一

Android/iOS と共通のエラーコードを使用します。`appmaster-parent/docs/` で定義されたエラーコードを参照してください。

```kotlin
// エラーコード定数
object ErrorCodes {
    // 認証関連
    const val UNAUTHORIZED = "UNAUTHORIZED"
    const val INVALID_TOKEN = "INVALID_TOKEN"
    const val TOKEN_EXPIRED = "TOKEN_EXPIRED"

    // ユーザー関連
    const val USER_NOT_FOUND = "USER_NOT_FOUND"
    const val EMAIL_ALREADY_EXISTS = "EMAIL_ALREADY_EXISTS"

    // バリデーション
    const val VALIDATION_ERROR = "VALIDATION_ERROR"
    const val INVALID_EMAIL = "INVALID_EMAIL"

    // サーバーエラー
    const val INTERNAL_ERROR = "INTERNAL_ERROR"
}
```
