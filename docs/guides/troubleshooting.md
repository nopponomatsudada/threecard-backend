# トラブルシューティングガイド

## ビルドエラー

### 1. JVM バージョン不一致

```
Inconsistent JVM-target compatibility detected for tasks 'compileJava' (21) and 'compileKotlin' (17)
```

**原因**: Java と Kotlin のターゲット JVM バージョンが一致していない

**解決策**:

```kotlin
// build.gradle.kts
kotlin {
    jvmToolchain(21)  // プロジェクトで使用する JVM バージョン
}
```

---

### 2. Unresolved reference: call

```
e: file:///.../routes/UserRoutes.kt:25:17 Unresolved reference: call
```

**原因**: Ktor 2.x/3.x で必要なインポートが不足

**解決策**:

```kotlin
// 必須インポートを追加
import io.ktor.server.application.*   // ← これが必要
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
```

---

### 3. カラム増減でのコンパイルエラー

```
e: Unresolved reference. None of the following candidates is applicable because of receiver type mismatch
```

**原因**: Exposed の update ブロック内でカラム参照が正しくない

**解決策**:

```kotlin
// ❌ 間違い
UserTable.update({ UserTable.id eq userId }) {
    it[likeCount] = likeCount + 1
}

// ✅ 正しい（テーブル名でカラムを修飾 + SqlExpressionBuilder をインポート）
import org.jetbrains.exposed.sql.SqlExpressionBuilder.plus

UserTable.update({ UserTable.id eq userId }) {
    it[likeCount] = UserTable.likeCount + 1
}
```

---

### 4. limit/offset でのエラー

```
e: Unresolved reference: it
```

**原因**: Exposed の `limit().offset()` チェーンが正しく動作しない

**解決策**:

```kotlin
// ❌ 問題あり
.limit(pageSize)
.offset(offset)
.map { it.toUser() }

// ✅ 正しい（2引数形式を使用）
.limit(pageSize, offset)
.map { row -> row.toUser() }
```

---

### 5. Shadow Plugin が見つからない

```
Could not find com.github.johnrengelman.shadow
```

**原因**: プラグイン名が変更された

**解決策**:

```kotlin
// ❌ 古いプラグイン
id("com.github.johnrengelman.shadow") version "8.1.1"

// ✅ 新しいプラグイン
id("com.gradleup.shadow") version "8.3.5"
```

---

## 実行時エラー

### 6. Database connection failed

```
org.h2.jdbc.JdbcSQLException: Connection is broken
```

**原因**: データベース接続設定が間違っている

**解決策**:

1. 環境変数を確認:
   ```bash
   echo $DATABASE_URL
   echo $DATABASE_USER
   ```

2. application.conf を確認:
   ```hocon
   database {
       url = "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1"
       driver = "org.h2.Driver"
   }
   ```

3. Docker コンテナが起動しているか確認:
   ```bash
   docker ps
   ```

---

### 7. JWT 認証失敗

```
io.ktor.server.auth.jwt.JWTAuthenticationProvider: Token verification failed
```

**原因**: JWT トークンが無効、または設定が間違っている

**解決策**:

1. JWT_SECRET が一致しているか確認
2. トークンの有効期限を確認
3. issuer, audience が一致しているか確認

```kotlin
// Security.kt
install(Authentication) {
    jwt("auth-jwt") {
        verifier(
            JWT.require(Algorithm.HMAC256(jwtSecret))
                .withIssuer(jwtIssuer)
                .withAudience(jwtAudience)
                .build()
        )
    }
}
```

---

### 8. Koin: No definition found

```
org.koin.core.error.NoBeanDefFoundException: No definition found for class 'UserRepository'
```

**原因**: DI モジュールに登録されていない、または型が一致していない

**解決策**:

1. モジュールが登録されているか確認:
   ```kotlin
   install(Koin) {
       modules(repositoryModule, useCaseModule)  // 登録漏れがないか
   }
   ```

2. インターフェースと実装の型を確認:
   ```kotlin
   // ✅ インターフェースに対してバインド
   single<UserRepository> { UserRepositoryImpl(get()) }

   // ❌ 実装クラスでバインドすると inject<UserRepository> で取得できない
   single { UserRepositoryImpl(get()) }
   ```

---

### 9. CORS エラー

```
Access to fetch at 'http://localhost:8080/api' from origin 'http://localhost:3000' has been blocked by CORS policy
```

**原因**: CORS が正しく設定されていない

**解決策**:

```kotlin
// plugins/CORS.kt
install(CORS) {
    allowHost("localhost:3000")
    allowHost("localhost:5173")
    allowHeader(HttpHeaders.Authorization)
    allowHeader(HttpHeaders.ContentType)
    allowCredentials = true
    allowMethod(HttpMethod.Options)
    allowMethod(HttpMethod.Get)
    allowMethod(HttpMethod.Post)
    allowMethod(HttpMethod.Put)
    allowMethod(HttpMethod.Delete)
}
```

---

### 10. Serialization エラー

```
kotlinx.serialization.SerializationException: Serializer for class 'User' is not found
```

**原因**: `@Serializable` アノテーションが不足

**解決策**:

```kotlin
// DTO には @Serializable を付ける
@Serializable
data class UserResponse(
    val id: String,
    val name: String
)

// Domain Entity は DTO に変換してから返す
fun Route.userRoutes() {
    get("/{id}") {
        val user = getUserUseCase(id)
        call.respond(UserResponse.from(user))  // DTO に変換
    }
}
```

---

## テストエラー

### 11. テストでデータベースエラー

```
org.jetbrains.exposed.exceptions.ExposedSQLException: Table 'USERS' not found
```

**原因**: テスト用データベースが初期化されていない

**解決策**:

```kotlin
class UserRepositoryTest : FunSpec({
    beforeTest {
        // H2 インメモリDBを初期化
        Database.connect("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1", driver = "org.h2.Driver")
        transaction {
            SchemaUtils.create(UsersTable, VideosTable)
        }
    }

    afterTest {
        transaction {
            SchemaUtils.drop(UsersTable, VideosTable)
        }
    }
})
```

---

### 12. MockK でのエラー

```
io.mockk.MockKException: no answer found for
```

**原因**: モックの振る舞いが定義されていない

**解決策**:

```kotlin
val mockRepository = mockk<UserRepository>()

// ✅ coEvery で suspend 関数をモック
coEvery { mockRepository.findById(any()) } returns expectedUser

// ❌ every だと suspend 関数に対応できない
every { mockRepository.findById(any()) } returns expectedUser
```

---

## パフォーマンス問題

### 13. N+1 クエリ問題

```
DEBUG Exposed -- SELECT * FROM USERS WHERE ID = '1'
DEBUG Exposed -- SELECT * FROM USERS WHERE ID = '2'
DEBUG Exposed -- SELECT * FROM USERS WHERE ID = '3'
... (大量のクエリ)
```

**原因**: ループ内で個別にクエリを実行している

**解決策**:

```kotlin
// ❌ N+1 問題
videos.map { video ->
    val author = userRepository.findById(video.authorId)  // N回クエリ
    VideoWithAuthor(video, author)
}

// ✅ 一括取得
val authorIds = videos.map { it.authorId }
val authors = userRepository.findByIds(authorIds)  // 1回のクエリ
val authorMap = authors.associateBy { it.id }

videos.map { video ->
    VideoWithAuthor(video, authorMap[video.authorId])
}
```

---

### 14. メモリ不足

```
java.lang.OutOfMemoryError: Java heap space
```

**原因**: 大量データを一度に取得している

**解決策**:

1. ページネーションを使用
2. ストリーミング処理を使用
3. JVM ヒープサイズを増加:
   ```properties
   # gradle.properties
   org.gradle.jvmargs=-Xmx2048m
   ```

---

## デバッグ Tips

### SQL クエリのログ出力

```xml
<!-- logback.xml -->
<logger name="Exposed" level="DEBUG"/>
```

### Ktor リクエスト/レスポンスのログ

```kotlin
install(CallLogging) {
    level = Level.DEBUG
    filter { call -> call.request.path().startsWith("/api") }
}
```

### 環境変数の確認

```kotlin
fun Application.module() {
    log.info("DATABASE_URL: ${System.getenv("DATABASE_URL")}")
    log.info("JWT_ISSUER: ${environment.config.property("jwt.issuer").getString()}")
}
```
