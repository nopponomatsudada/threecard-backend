# Quick Reference

## パッケージ構成

```
src/main/kotlin/com/example/appmaster/
├── Application.kt
├── plugins/
│   ├── Routing.kt
│   ├── Serialization.kt
│   ├── Authentication.kt
│   ├── Database.kt
│   ├── StatusPages.kt
│   └── Koin.kt
├── domain/
│   ├── model/
│   │   ├── entity/
│   │   └── valueobject/
│   ├── repository/
│   ├── usecase/
│   └── error/
├── data/
│   ├── entity/
│   ├── dao/
│   └── repository/
└── routes/
    ├── dto/
    │   ├── request/
    │   └── response/
    └── *Routes.kt
```

## 命名規則

| 種類 | 命名 | 例 |
|------|------|-----|
| Entity | 名詞 | `User`, `Task` |
| ValueObject | 名詞 | `Email`, `UserId` |
| Repository Interface | `*Repository` | `UserRepository` |
| Repository Impl | `*RepositoryImpl` | `UserRepositoryImpl` |
| UseCase | `動詞 + 名詞 + UseCase` | `CreateUserUseCase` |
| DAO | `*Dao` | `UserDao` |
| Table | `*Table` | `UserTable` |
| Request DTO | `*Request` | `CreateUserRequest` |
| Response DTO | `*Response` | `UserResponse` |
| Error | `DomainError.*` | `DomainError.UserNotFound` |

## 依存関係マトリクス

```
         Domain  Data  Routes  Ktor  Exposed  Koin
Domain     -      ❌     ❌     ❌      ❌      ❌
Data       ✅      -     ❌     ❌      ✅      ✅
Routes     ✅     ❌      -     ✅      ❌      ✅
```

## テスト戦略

| レイヤー | テスト種別 | モック |
|---------|----------|--------|
| Domain | Unit | なし |
| Data | Integration | H2 DB |
| Routes | API Test | UseCase |
