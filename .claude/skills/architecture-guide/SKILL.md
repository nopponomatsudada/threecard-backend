---
name: architecture-guide
description: >
  Clean Architecture リファレンス。機能実装時やコードレビュー時に自動ロードされ、
  レイヤー責務、依存関係ルール、実装パターンを提供する。
  トリガー: "アーキテクチャ", "どのレイヤー", "依存関係"
---

# Architecture Guide

Ktor + Kotlin バックエンドの Clean Architecture ガイド。

## Quick Reference

### レイヤー構成

```
Routes 層 (HTTP) → Domain 層 (Business) ← Data 層 (Persistence)
```

### 依存関係ルール

| From | To | 許可 |
|------|----|----|
| Routes | Domain | ✅ |
| Data | Domain | ✅ |
| Domain | Routes | ❌ |
| Domain | Data | ❌ |
| Routes | Data | ❌ |

## レイヤー責務

### Domain 層

**責務**: ビジネスロジック、ドメインルール

**含むもの**:
- Entity: ビジネスエンティティ
- ValueObject: 値オブジェクト
- Repository Interface: 永続化の抽象
- UseCase: ビジネス操作
- DomainError: ビジネスエラー

**禁止事項**:
- Ktor 依存
- Exposed 依存
- HTTP 関連コード

```kotlin
// ✅ Good - Pure Kotlin
data class User(val id: UserId, val email: Email)

// ❌ Bad - Ktor 依存
import io.ktor.server.response.*
```

### Data 層

**責務**: データ永続化、外部API連携

**含むもの**:
- Exposed Table: テーブル定義
- DAO: データアクセス
- Repository Implementation: Repository の実装
- External API Client: 外部サービスクライアント

```kotlin
// Repository 実装
class UserRepositoryImpl(private val dao: UserDao) : UserRepository {
    override suspend fun findById(id: UserId): User? =
        dao.findById(id)
}
```

### Routes 層

**責務**: HTTP リクエスト/レスポンス処理

**含むもの**:
- Route Definition: エンドポイント定義
- Request DTO: リクエストボディ
- Response DTO: レスポンスボディ
- Input Validation: 入力検証

```kotlin
// Route 定義
fun Route.userRoutes() {
    val getUserUseCase by inject<GetUserUseCase>()

    get("/{id}") {
        val id = call.parameters["id"]!!
        getUserUseCase(UserId(id))
            .onSuccess { call.respond(UserResponse.from(it)) }
            .onFailure { call.respondError(it) }
    }
}
```

## Decision Tree

### 「この処理はどこに置く？」

```
処理内容を確認
    │
    ├─ HTTP リクエスト/レスポンスに関係する？
    │   └─ Yes → Routes 層
    │
    ├─ データベース/外部APIアクセスに関係する？
    │   └─ Yes → Data 層
    │
    └─ ビジネスルール/ドメインロジック？
        └─ Yes → Domain 層
```

### 具体例

| 処理 | レイヤー |
|------|---------|
| リクエストボディのパース | Routes |
| JWT トークン検証 | Routes (Plugin) |
| メールアドレスの形式検証 | Domain (ValueObject) |
| ユーザー重複チェック | Domain (UseCase) |
| DB からユーザー取得 | Data |
| レスポンス JSON 生成 | Routes |

## アンチパターン

### ❌ UseCase から HTTP レスポンス返却

```kotlin
// Bad
class GetUserUseCase {
    suspend fun invoke(id: String): Response {
        return Response.ok(user)  // HTTP 依存
    }
}

// Good
class GetUserUseCase {
    suspend fun invoke(id: UserId): Result<User> {
        return Result.success(user)
    }
}
```

### ❌ Routes から Data 直接アクセス

```kotlin
// Bad
get("/{id}") {
    val user = userDao.findById(id)  // DAO 直接呼び出し
}

// Good
get("/{id}") {
    getUserUseCase(UserId(id))  // UseCase 経由
}
```

### ❌ Domain で Exposed 使用

```kotlin
// Bad - Domain 層
interface UserRepository {
    fun findById(id: UserId): ResultRow?  // Exposed 依存
}

// Good
interface UserRepository {
    suspend fun findById(id: UserId): User?
}
```

## Reference

- [Quick Reference](reference/quick-reference.md)
- [Domain Examples](examples/domain-examples.kt)
