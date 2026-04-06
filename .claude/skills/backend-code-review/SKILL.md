---
name: backend-code-review
description: >
  Kotlin + Ktor バックエンドのコードレビューを行うスキル。
  アーキテクチャ違反、セキュリティ問題、Ktorベストプラクティスをチェック。
  OWASP Top 10 に基づくセキュリティ検証を含む。
  トリガー: "レビュー", "コードチェック", "PRレビュー", "セキュリティチェック"
---

# Backend Code Review

Ktor + Kotlin バックエンドのコードレビューチェックリスト。

## レビュー手順

### Step 1: セキュリティチェック（OWASP Top 10）

**最優先でチェック。問題があれば即時修正。**

#### A01: Broken Access Control（アクセス制御の不備）

```
- [ ] すべてのエンドポイントで認証が要求されているか
- [ ] リソースアクセス時に所有者チェックがあるか
- [ ] 管理者機能へのロールチェックがあるか
- [ ] 水平権限昇格（他ユーザーのデータアクセス）が防止されているか
```

```kotlin
// ❌ Critical - 所有者チェックなし
get("/users/{id}/profile") {
    val userId = call.parameters["id"]!!
    val profile = getProfileUseCase(UserId(userId))  // 誰でも取得可能
    call.respond(profile)
}

// ✅ Correct - 所有者チェックあり
get("/users/{id}/profile") {
    val requestedId = call.parameters["id"]!!
    val currentUserId = call.principal<JWTPrincipal>()?.payload?.getClaim("userId")?.asString()

    if (requestedId != currentUserId && !isAdmin(call)) {
        throw ForbiddenException("Access denied")
    }

    val profile = getProfileUseCase(UserId(requestedId))
    call.respond(profile)
}
```

#### A02: Cryptographic Failures（暗号化の失敗）

```
- [ ] パスワードは BCrypt でハッシュ化されているか（コスト係数 12 以上）
- [ ] JWT 署名アルゴリズムは適切か（RS256 推奨、最低 HS256）
- [ ] 機密データは平文で保存されていないか
- [ ] TLS 1.2 以上が使用されているか
```

```kotlin
// ❌ Critical - 平文パスワード保存
UserTable.insert {
    it[password] = user.password.value  // 平文！
}

// ✅ Correct - ハッシュ化
UserTable.insert {
    it[passwordHash] = passwordHasher.hash(user.password.value)
}
```

#### A03: Injection（インジェクション）

```
- [ ] SQL クエリでパラメータバインディングを使用しているか
- [ ] 生の SQL 文字列連結がないか
- [ ] 外部コマンド実行時に引数をエスケープしているか
```

```kotlin
// ❌ Critical - SQL インジェクション
fun findByName(name: String) = transaction {
    exec("SELECT * FROM users WHERE name = '$name'")  // 危険！
}

// ✅ Correct - Exposed パラメータバインディング
fun findByName(name: String) = transaction {
    UserTable.select { UserTable.name eq name }.toList()
}
```

#### A05: Security Misconfiguration（セキュリティ設定ミス）

```
- [ ] セキュリティヘッダーが設定されているか
- [ ] CORS が適切に設定されているか
- [ ] 本番環境でデバッグモードが無効か
- [ ] エラーメッセージで詳細情報が漏洩していないか
```

```kotlin
// ❌ Warning - セキュリティヘッダーなし
fun Application.module() {
    configureRouting()
}

// ✅ Correct - セキュリティヘッダー設定
fun Application.module() {
    install(DefaultHeaders) {
        header("X-Frame-Options", "DENY")
        header("X-Content-Type-Options", "nosniff")
        header("Strict-Transport-Security", "max-age=31536000")
    }
    configureRouting()
}
```

#### A07: Identification and Authentication Failures（認証の失敗）

```
- [ ] ログイン試行回数が制限されているか
- [ ] パスワード強度要件があるか
- [ ] セッション/トークンの有効期限が適切か
- [ ] ログアウト時にトークンが無効化されるか
```

```kotlin
// ❌ Warning - レート制限なし
post("/auth/login") {
    val request = call.receive<LoginRequest>()
    val result = loginUseCase(request.email, request.password)
    // ...
}

// ✅ Correct - レート制限あり
rateLimit(RateLimitName("auth")) {
    post("/auth/login") {
        val request = call.receive<LoginRequest>()
        val result = loginUseCase(request.email, request.password)
        // ...
    }
}
```

#### A09: Security Logging and Monitoring Failures（ロギングの失敗）

```
- [ ] 認証イベント（成功/失敗）がログに記録されているか
- [ ] 機密情報がログに出力されていないか
- [ ] エラー/例外がログに記録されているか
- [ ] 監査ログが実装されているか
```

```kotlin
// ❌ Critical - パスワードがログに出力
logger.info("Login attempt: email=${email}, password=${password}")

// ✅ Correct - 機密情報をマスク
logger.info("Login attempt: email=${email}, ip=${ipAddress}")
auditService.log(AuditAction.LOGIN_ATTEMPT, "auth", userId = null, ipAddress = ipAddress)
```

### Step 2: アーキテクチャチェック

```
- [ ] 依存方向は正しいか（Routes → Domain ← Data）
- [ ] Domain 層に外部依存がないか
- [ ] Routes から Data への直接アクセスがないか
- [ ] UseCase は単一責任か
```

### Step 3: Ktor ベストプラクティス

```
- [ ] suspend 関数が適切に使用されているか
- [ ] StatusPages でエラーハンドリングされているか
- [ ] ContentNegotiation が設定されているか
- [ ] CORS が適切に設定されているか
```

### Step 4: コード品質

```
- [ ] 命名規則に従っているか
- [ ] Result 型でエラーハンドリングされているか
- [ ] 適切なテストがあるか
- [ ] ドキュメントコメントがあるか
```

---

## Critical Issues (即時修正)

### 1. 認証バイパス

```kotlin
// ❌ Critical - 認証なしでアクセス可能
route("/api/v1/users") {
    get("/{id}") { /* 認証チェックなし */ }
}

// ✅ Correct
authenticate("jwt") {
    route("/api/v1/users") {
        get("/{id}") { /* 認証済み */ }
    }
}
```

### 2. 権限チェック漏れ

```kotlin
// ❌ Critical - 他ユーザーのデータを取得可能
delete("/users/{id}") {
    val userId = call.parameters["id"]!!
    deleteUserUseCase(UserId(userId))  // 誰でも削除可能！
    call.respond(HttpStatusCode.NoContent)
}

// ✅ Correct
delete("/users/{id}") {
    val targetUserId = call.parameters["id"]!!
    val currentUserId = call.principal<JWTPrincipal>()?.getClaim("userId")

    if (targetUserId != currentUserId && !isAdmin(call)) {
        call.respond(HttpStatusCode.Forbidden)
        return@delete
    }

    deleteUserUseCase(UserId(targetUserId))
    call.respond(HttpStatusCode.NoContent)
}
```

### 3. SQL インジェクション

```kotlin
// ❌ Critical - 文字列連結
fun search(query: String) = transaction {
    exec("SELECT * FROM users WHERE name LIKE '%$query%'")
}

// ✅ Correct - パラメータバインディング
fun search(query: String) = transaction {
    UserTable.select { UserTable.name like "%$query%" }.toList()
}
```

### 4. 機密情報のログ出力

```kotlin
// ❌ Critical
logger.info("User login: $email, password: $password, token: $token")

// ✅ Correct
logger.info("User login: email=$email, ip=$ipAddress")
```

### 5. Domain 層の外部依存

```kotlin
// ❌ Critical - Ktor 依存
package com.example.domain.usecase
import io.ktor.server.response.*

// ❌ Critical - Exposed 依存
package com.example.domain.repository
import org.jetbrains.exposed.sql.*
```

---

## Warning Issues (修正推奨)

### 1. レート制限なし

```kotlin
// ⚠️ Warning
post("/auth/login") { ... }

// ✅ Better
rateLimit(RateLimitName("auth")) {
    post("/auth/login") { ... }
}
```

### 2. UseCase で複数操作

```kotlin
// ⚠️ Warning - 責任過多
class UserUseCase {
    fun createUser() { ... }
    fun updateUser() { ... }
    fun deleteUser() { ... }
}

// ✅ Better - 単一責任
class CreateUserUseCase { ... }
class UpdateUserUseCase { ... }
class DeleteUserUseCase { ... }
```

### 3. 例外スロー

```kotlin
// ⚠️ Warning - 例外スロー
suspend fun invoke(id: UserId): User {
    return repository.findById(id)
        ?: throw DomainError.UserNotFound(id)
}

// ✅ Better - Result 型
suspend fun invoke(id: UserId): Result<User> {
    val user = repository.findById(id)
        ?: return Result.failure(DomainError.UserNotFound(id))
    return Result.success(user)
}
```

### 4. 入力バリデーションなし

```kotlin
// ⚠️ Warning
data class CreateUserRequest(val email: String, val password: String)

// ✅ Better - ValueObject でバリデーション
fun CreateUserRequest.toDomain(): Result<CreateUserCommand> {
    val email = Email.create(this.email).getOrElse { return Result.failure(it) }
    val password = Password.create(this.password).getOrElse { return Result.failure(it) }
    return Result.success(CreateUserCommand(email, password))
}
```

---

## OWASP Top 10 クイックリファレンス

| ID | 脆弱性 | 主なチェックポイント |
|----|--------|---------------------|
| A01 | Broken Access Control | 認証必須、所有者チェック、RBAC |
| A02 | Cryptographic Failures | BCrypt、RS256、TLS |
| A03 | Injection | パラメータバインディング |
| A04 | Insecure Design | Clean Architecture |
| A05 | Security Misconfiguration | ヘッダー、CORS、デバッグ無効 |
| A06 | Vulnerable Components | 依存関係更新、脆弱性スキャン |
| A07 | Authentication Failures | レート制限、パスワード強度 |
| A08 | Software Integrity | 依存関係ロック、署名 |
| A09 | Logging Failures | 監査ログ、機密情報マスク |
| A10 | SSRF | URL ホワイトリスト |

---

## セキュリティレビュー出力フォーマット

レビュー結果は以下のフォーマットで報告してください：

```markdown
## セキュリティレビュー結果

### Critical Issues (即時修正必須)

1. **[A01] 認証バイパス** - `UserRoutes.kt:45`
   - 問題: `/users/{id}` エンドポイントに認証がない
   - 修正: `authenticate("jwt")` ブロックで囲む

### Warning Issues (修正推奨)

1. **[A07] レート制限なし** - `AuthRoutes.kt:12`
   - 問題: ログインエンドポイントにレート制限がない
   - 修正: `rateLimit(RateLimitName("auth"))` を追加

### Info (改善提案)

1. **監査ログ追加** - `UserRoutes.kt`
   - 提案: ユーザー操作の監査ログを追加
```

---

## Checklists

- [Architecture Checklist](checklists/architecture-checklist.md)
- [Security Checklist](checklists/security-checklist.md)
- [OWASP Top 10 Checklist](checklists/owasp-checklist.md)
- [Ktor Checklist](checklists/ktor-checklist.md)

## Reference

- [セキュリティガイド](../../../docs/guides/security.md)
- [OWASP Top 10](https://owasp.org/Top10/)
