# アーキテクチャガイドライン

## 概要

AppMaster Backend は **Clean Architecture** をベースとした3層アーキテクチャを採用しています。

## レイヤー構成

```
┌─────────────────────────────────────────────────┐
│                   Routes 層                      │
│     HTTP リクエスト/レスポンス、DTO 変換          │
│     認証・認可、入力バリデーション                 │
└─────────────────────────────────────────────────┘
                      │ 依存
                      ▼
┌─────────────────────────────────────────────────┐
│                  Domain 層                       │
│     Entity、ValueObject、UseCase                 │
│     Repository インターフェース、DomainError      │
└─────────────────────────────────────────────────┘
                      ▲ 依存
                      │
┌─────────────────────────────────────────────────┐
│                   Data 層                        │
│     Repository 実装、Exposed Entity             │
│     DAO、外部 API クライアント                    │
└─────────────────────────────────────────────────┘
```

## 依存関係ルール

### 許可される依存

| From | To | 説明 |
|------|----|----|
| Routes | Domain | UseCase を呼び出し、結果を DTO に変換 |
| Data | Domain | Repository インターフェースを実装 |

### 禁止される依存

| From | To | 理由 |
|------|----|----|
| Domain | Routes | ドメインは HTTP 層に依存してはいけない |
| Domain | Data | ドメインは永続化の詳細に依存してはいけない |
| Routes | Data | Routes は Data を直接参照してはいけない（UseCase 経由で） |

## パッケージ構成

```
src/main/kotlin/com/example/appmaster/
├── Application.kt
├── plugins/
│   ├── Routing.kt
│   ├── Serialization.kt
│   ├── Authentication.kt
│   ├── Database.kt
│   └── Koin.kt
├── domain/
│   ├── model/
│   │   ├── entity/
│   │   │   └── User.kt
│   │   └── valueobject/
│   │       └── Email.kt
│   ├── repository/
│   │   └── UserRepository.kt
│   ├── usecase/
│   │   ├── GetUserUseCase.kt
│   │   └── CreateUserUseCase.kt
│   └── error/
│       └── DomainError.kt
├── data/
│   ├── entity/
│   │   └── UserTable.kt
│   ├── dao/
│   │   └── UserDao.kt
│   └── repository/
│       └── UserRepositoryImpl.kt
└── routes/
    ├── AuthRoutes.kt
    ├── UserRoutes.kt
    └── dto/
        ├── request/
        │   └── CreateUserRequest.kt
        └── response/
            └── UserResponse.kt
```

## 設計原則

### 1. 依存性逆転の原則（DIP）

Domain 層は Repository の**インターフェース**を定義し、Data 層が実装を提供します。

```kotlin
// domain/repository/UserRepository.kt
interface UserRepository {
    suspend fun findById(id: UserId): User?
    suspend fun save(user: User): User
}

// data/repository/UserRepositoryImpl.kt
class UserRepositoryImpl(private val dao: UserDao) : UserRepository {
    override suspend fun findById(id: UserId): User? = dao.findById(id)?.toDomain()
    override suspend fun save(user: User): User = dao.upsert(user.toEntity()).toDomain()
}
```

### 2. 単一責任の原則（SRP）

各クラスは1つの責任のみを持ちます:

- **UseCase**: 1つのビジネス操作
- **Repository**: 1つのエンティティの永続化
- **DTO**: 1つのリクエスト/レスポンス形式

### 3. Pure Domain

Domain 層は外部依存を持たない Pure Kotlin で記述します:

- ❌ Ktor 依存
- ❌ Exposed 依存
- ❌ Koin 依存
- ✅ Kotlin stdlib のみ
- ✅ coroutines-core のみ許可

## DI 設計

Koin を使用した依存性注入:

```kotlin
// plugins/Koin.kt
val domainModule = module {
    single { GetUserUseCase(get()) }
    single { CreateUserUseCase(get()) }
}

val dataModule = module {
    single { UserDao() }
    single<UserRepository> { UserRepositoryImpl(get()) }
}

val routesModule = module {
    // Routes は DI コンテナから UseCase を取得
}
```

## アーキテクチャ違反チェック

以下の違反を検出した場合、修正が必要です:

### Critical (修正必須)

- Domain 層から Ktor/Exposed のインポート
- Routes から Data への直接依存
- UseCase からの HTTP レスポンス返却

### Warning (修正推奨)

- UseCase に複数の責任がある
- Repository に ビジネスロジックが含まれている
- DTO に バリデーションロジックが含まれている

## テスト戦略

各レイヤーは独立してテスト可能です:

| レイヤー | テスト種別 | モック対象 |
|---------|----------|----------|
| Domain | Unit Test | なし（Pure） |
| Data | Integration Test | Database |
| Routes | API Test | UseCase |

詳細は [testing-guidelines.md](testing-guidelines.md) を参照。
