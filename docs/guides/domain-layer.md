# Domain 層ガイド

## 概要

Domain 層はビジネスロジックの中心です。外部依存を持たない Pure Kotlin で記述します。

## 構成要素

### 1. Entity

ビジネスドメインの主要な概念を表すクラスです。

```kotlin
// domain/model/entity/User.kt
data class User(
    val id: UserId,
    val email: Email,
    val name: UserName,
    val status: UserStatus,
    val createdAt: Instant,
    val updatedAt: Instant
) {
    fun activate(): User = copy(status = UserStatus.ACTIVE, updatedAt = Instant.now())
    fun deactivate(): User = copy(status = UserStatus.INACTIVE, updatedAt = Instant.now())

    companion object {
        fun create(email: Email, name: UserName): User = User(
            id = UserId.generate(),
            email = email,
            name = name,
            status = UserStatus.PENDING,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
    }
}

enum class UserStatus {
    PENDING, ACTIVE, INACTIVE
}
```

### 2. Value Object

不変で、値の等価性で比較されるオブジェクトです。バリデーションロジックを含みます。

```kotlin
// domain/model/valueobject/Email.kt
@JvmInline
value class Email private constructor(val value: String) {
    companion object {
        private val EMAIL_REGEX = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\$")

        fun create(value: String): Result<Email> {
            return if (EMAIL_REGEX.matches(value)) {
                Result.success(Email(value.lowercase()))
            } else {
                Result.failure(DomainError.InvalidEmail(value))
            }
        }
    }
}

// domain/model/valueobject/UserId.kt
@JvmInline
value class UserId(val value: String) {
    companion object {
        fun generate(): UserId = UserId(UUID.randomUUID().toString())
    }
}

// domain/model/valueobject/UserName.kt
@JvmInline
value class UserName private constructor(val value: String) {
    companion object {
        private const val MIN_LENGTH = 2
        private const val MAX_LENGTH = 50

        fun create(value: String): Result<UserName> {
            val trimmed = value.trim()
            return when {
                trimmed.length < MIN_LENGTH -> Result.failure(DomainError.UserNameTooShort(MIN_LENGTH))
                trimmed.length > MAX_LENGTH -> Result.failure(DomainError.UserNameTooLong(MAX_LENGTH))
                else -> Result.success(UserName(trimmed))
            }
        }
    }
}
```

### 3. Repository インターフェース

永続化の抽象を定義します。実装は Data 層で提供します。

```kotlin
// domain/repository/UserRepository.kt
interface UserRepository {
    suspend fun findById(id: UserId): User?
    suspend fun findByEmail(email: Email): User?
    suspend fun save(user: User): User
    suspend fun delete(id: UserId)
    suspend fun findAll(limit: Int = 100, offset: Int = 0): List<User>
}
```

### 4. UseCase

1つのビジネス操作を表します。

```kotlin
// domain/usecase/GetUserUseCase.kt
class GetUserUseCase(private val userRepository: UserRepository) {
    suspend operator fun invoke(id: UserId): Result<User> {
        val user = userRepository.findById(id)
            ?: return Result.failure(DomainError.UserNotFound(id))
        return Result.success(user)
    }
}

// domain/usecase/CreateUserUseCase.kt
class CreateUserUseCase(private val userRepository: UserRepository) {
    suspend operator fun invoke(email: Email, name: UserName): Result<User> {
        // 重複チェック
        userRepository.findByEmail(email)?.let {
            return Result.failure(DomainError.EmailAlreadyExists(email))
        }

        val user = User.create(email, name)
        return Result.success(userRepository.save(user))
    }
}
```

### 5. Domain Error

ビジネスルール違反を表すエラーです。

```kotlin
// domain/error/DomainError.kt
sealed class DomainError : Exception() {
    // バリデーションエラー
    data class InvalidEmail(val email: String) : DomainError()
    data class UserNameTooShort(val minLength: Int) : DomainError()
    data class UserNameTooLong(val maxLength: Int) : DomainError()

    // ビジネスルールエラー
    data class UserNotFound(val id: UserId) : DomainError()
    data class EmailAlreadyExists(val email: Email) : DomainError()
    data class UserAlreadyActive(val id: UserId) : DomainError()

    // エラーコード（API レスポンス用）
    val code: String
        get() = when (this) {
            is InvalidEmail -> "INVALID_EMAIL"
            is UserNameTooShort -> "USERNAME_TOO_SHORT"
            is UserNameTooLong -> "USERNAME_TOO_LONG"
            is UserNotFound -> "USER_NOT_FOUND"
            is EmailAlreadyExists -> "EMAIL_ALREADY_EXISTS"
            is UserAlreadyActive -> "USER_ALREADY_ACTIVE"
        }

    override val message: String
        get() = when (this) {
            is InvalidEmail -> "無効なメールアドレス形式です: $email"
            is UserNameTooShort -> "ユーザー名は${minLength}文字以上必要です"
            is UserNameTooLong -> "ユーザー名は${maxLength}文字以下にしてください"
            is UserNotFound -> "ユーザーが見つかりません: ${id.value}"
            is EmailAlreadyExists -> "このメールアドレスは既に使用されています"
            is UserAlreadyActive -> "ユーザーは既にアクティブです"
        }
}
```

## 設計原則

### Pure Kotlin

Domain 層は外部ライブラリに依存しません:

```kotlin
// ✅ 許可
import java.util.UUID
import java.time.Instant
import kotlinx.coroutines.flow.Flow

// ❌ 禁止
import io.ktor.*          // Ktor 依存
import org.jetbrains.exposed.*  // Exposed 依存
import org.koin.*         // Koin 依存
```

### Result 型の使用

エラーハンドリングには `Result` 型を使用します:

```kotlin
// UseCase の戻り値は Result<T>
suspend fun invoke(id: UserId): Result<User>

// 呼び出し側
getUserUseCase(id)
    .onSuccess { user -> /* 成功処理 */ }
    .onFailure { error -> /* エラー処理 */ }
```

### 不変性

Entity と ValueObject は不変（immutable）です:

```kotlin
// ❌ 可変
class User {
    var status: UserStatus = UserStatus.PENDING
    fun activate() { status = UserStatus.ACTIVE }
}

// ✅ 不変
data class User(val status: UserStatus) {
    fun activate(): User = copy(status = UserStatus.ACTIVE)
}
```

## テスト

Domain 層は Pure Kotlin のため、モックなしでテスト可能です:

```kotlin
class CreateUserUseCaseTest : FunSpec({
    test("should create user with valid input") {
        val repository = InMemoryUserRepository()
        val useCase = CreateUserUseCase(repository)

        val email = Email.create("test@example.com").getOrThrow()
        val name = UserName.create("Test User").getOrThrow()

        val result = useCase(email, name)

        result.isSuccess shouldBe true
        result.getOrNull()?.email shouldBe email
    }
})
```
