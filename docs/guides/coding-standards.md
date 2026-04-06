# コーディング規約

## 命名規則

### ファイル名

| 種類 | 規則 | 例 |
|-----|------|-----|
| Kotlin ファイル | PascalCase | `UserRepository.kt` |
| テストファイル | `*Test.kt` | `UserRepositoryTest.kt` |
| 設定ファイル | kebab-case | `application.conf` |

### クラス・インターフェース

| 種類 | 規則 | 例 |
|-----|------|-----|
| Entity | 名詞 | `User`, `Task` |
| ValueObject | 名詞 | `Email`, `UserId` |
| Repository | `*Repository` | `UserRepository` |
| UseCase | `動詞 + 名詞 + UseCase` | `CreateUserUseCase` |
| DTO | `*Request`, `*Response` | `CreateUserRequest` |
| Error | `*Error` | `DomainError` |

### エンティティ命名の注意（Kotlin stdlib との衝突回避）

以下の名前は Kotlin stdlib の型名と衝突するため、エンティティ名として使用しないこと:

| 避けるべき名前 | 衝突する型 | 代替案 |
|--------------|-----------|--------|
| `Collection` | `kotlin.collections.Collection` | `UserCollection`, `BucketCollection` |
| `Map` | `kotlin.collections.Map` | `LocationMap`, `SpotMap` |
| `Set` | `kotlin.collections.Set` | `SettingSet`, `PreferenceSet` |
| `List` | `kotlin.collections.List` | `WishList`, `TodoList` |
| `Result` | `kotlin.Result` | `OperationResult`, `DomainResult` |
| `Pair` | `kotlin.Pair` | ドメイン固有の名前を使用 |

**ルール**: `kotlin.*` や `kotlin.collections.*` と同名のエンティティは作成しない。`List<Collection>` のようなシグネチャで stdlib の型と解釈され、大量のコンパイルエラーが発生する。

### 関数

| 種類 | 規則 | 例 |
|-----|------|-----|
| 取得 | `get*`, `find*` | `findById()`, `getUser()` |
| 作成 | `create*` | `createUser()` |
| 更新 | `update*` | `updateUser()` |
| 削除 | `delete*` | `deleteUser()` |
| 変換 | `to*` | `toResponse()`, `toDomain()` |
| 検証 | `validate*`, `is*` | `validateEmail()`, `isActive()` |

### 変数

| 種類 | 規則 | 例 |
|-----|------|-----|
| 通常 | camelCase | `userName`, `createdAt` |
| 定数 | SCREAMING_SNAKE_CASE | `MAX_LENGTH`, `DEFAULT_LIMIT` |
| プライベート | `_` prefix 不要 | `private val name` |

## コードスタイル

### Kotlin スタイル

```kotlin
// ✅ Good
class UserRepository(
    private val dao: UserDao,
    private val mapper: UserMapper
) {
    suspend fun findById(id: UserId): User? {
        return dao.findById(id)?.let { mapper.toDomain(it) }
    }
}

// ❌ Bad - 一行が長すぎる
class UserRepository(private val dao: UserDao, private val mapper: UserMapper) {
    suspend fun findById(id: UserId): User? = dao.findById(id)?.let { mapper.toDomain(it) }
}
```

### インデント・空白

- インデント: 4 スペース
- 行の最大長: 120 文字
- 関数間: 1 行空ける
- クラス間: 2 行空ける

### import 順序

```kotlin
// 1. Kotlin stdlib
import kotlin.collections.*

// 2. Java stdlib
import java.time.*

// 3. サードパーティ
import io.ktor.server.application.*
import org.jetbrains.exposed.sql.*

// 4. プロジェクト内
import com.example.appmaster.domain.model.*
```

## ドキュメンテーション

### KDoc

```kotlin
/**
 * ユーザーを作成するユースケース。
 *
 * @property repository ユーザーリポジトリ
 */
class CreateUserUseCase(private val repository: UserRepository) {
    /**
     * 新しいユーザーを作成する。
     *
     * @param email ユーザーのメールアドレス
     * @param name ユーザー名
     * @return 作成されたユーザー、または失敗時はエラー
     * @throws DomainError.EmailAlreadyExists メールアドレスが既に使用されている場合
     */
    suspend operator fun invoke(email: Email, name: UserName): Result<User> {
        // ...
    }
}
```

### コメント

```kotlin
// ✅ 「なぜ」を説明する
// メールアドレスの重複チェックは、一意性制約エラーを防ぐために先に行う
repository.findByEmail(email)?.let {
    return Result.failure(DomainError.EmailAlreadyExists(email))
}

// ❌ 「何を」しているかの説明は不要（コードで明らか）
// メールアドレスで検索する
repository.findByEmail(email)
```

## エラーハンドリング

### Result 型を使用

```kotlin
// ✅ Good
suspend fun invoke(id: UserId): Result<User> {
    val user = repository.findById(id)
        ?: return Result.failure(DomainError.UserNotFound(id))
    return Result.success(user)
}

// ❌ Bad - null を返す
suspend fun invoke(id: UserId): User? {
    return repository.findById(id)
}

// ❌ Bad - 例外をスロー
suspend fun invoke(id: UserId): User {
    return repository.findById(id)
        ?: throw DomainError.UserNotFound(id)
}
```

### 早期リターン

```kotlin
// ✅ Good
suspend fun invoke(request: CreateUserRequest): Result<User> {
    val errors = request.validate()
    if (errors.isNotEmpty()) {
        return Result.failure(ValidationError(errors))
    }

    val email = Email.create(request.email).getOrElse {
        return Result.failure(it)
    }

    // メインロジック
    return createUser(email)
}

// ❌ Bad - ネストが深い
suspend fun invoke(request: CreateUserRequest): Result<User> {
    val errors = request.validate()
    if (errors.isEmpty()) {
        Email.create(request.email).fold(
            onSuccess = { email ->
                // メインロジック
            },
            onFailure = { error ->
                return Result.failure(error)
            }
        )
    } else {
        return Result.failure(ValidationError(errors))
    }
}
```

## テスト

### テストメソッド命名

```kotlin
// ✅ Good - 振る舞いを説明
test("should return user when user exists")
test("should throw UserNotFound when user does not exist")
test("should create user with valid email and name")

// ❌ Bad - 曖昧
test("test1")
test("getUserTest")
```

### Arrange-Act-Assert

```kotlin
test("should create user with valid input") {
    // Arrange
    val repository = InMemoryUserRepository()
    val useCase = CreateUserUseCase(repository)
    val email = Email.create("test@example.com").getOrThrow()
    val name = UserName.create("Test").getOrThrow()

    // Act
    val result = useCase(email, name)

    // Assert
    result.isSuccess shouldBe true
    result.getOrNull()?.email shouldBe email
}
```

## 禁止事項

### ハードコード禁止

```kotlin
// ❌ Bad
val token = "secret-token-123"
val dbUrl = "jdbc:postgresql://localhost:5432/db"

// ✅ Good
val token = environment.config.property("jwt.secret").getString()
val dbUrl = environment.config.property("database.url").getString()
```

### プリミティブ型の直接使用禁止（Domain 層）

```kotlin
// ❌ Bad
fun findById(id: String): User?
fun createUser(email: String, name: String): User

// ✅ Good
fun findById(id: UserId): User?
fun createUser(email: Email, name: UserName): User
```

### 可変状態の禁止（Domain 層）

```kotlin
// ❌ Bad
class User {
    var status: UserStatus = UserStatus.PENDING
    fun activate() { status = UserStatus.ACTIVE }
}

// ✅ Good
data class User(val status: UserStatus) {
    fun activate(): User = copy(status = UserStatus.ACTIVE)
}
```
