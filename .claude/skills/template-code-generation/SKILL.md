---
name: template-code-generation
description: >
  テンプレートベースのコード生成スキル。Entity, UseCase, Repository などの
  ボイラープレートコードを自動生成する。
  トリガー: "Entity生成", "UseCase作成", "テンプレートから生成"
---

# Template Code Generation

テンプレートからコードを自動生成するスキル。

## 使用方法

### 1. Entity 生成

```
「User エンティティを生成してください」
```

生成されるファイル:
- `domain/model/entity/User.kt`
- `domain/model/valueobject/UserId.kt`

### 2. Repository 生成

```
「User の Repository を生成してください」
```

生成されるファイル:
- `domain/repository/UserRepository.kt`
- `data/repository/UserRepositoryImpl.kt`

### 3. UseCase 生成

```
「GetUserUseCase を生成してください」
```

生成されるファイル:
- `domain/usecase/GetUserUseCase.kt`

### 4. CRUD 一式生成

```
「Task の CRUD 一式を生成してください」
```

生成されるファイル:
- Entity, ValueObject
- Repository (interface + impl)
- UseCase (Create, Get, Update, Delete, List)
- DTO (Request, Response)
- Routes

## テンプレート変数

| 変数 | 説明 | 例 |
|------|------|-----|
| `{EntityName}` | Entity 名（PascalCase） | `User` |
| `{entityName}` | Entity 名（camelCase） | `user` |
| `{entity_name}` | Entity 名（snake_case） | `user` |
| `{ENTITY_NAME}` | Entity 名（SCREAMING_SNAKE_CASE） | `USER` |
| `{table_name}` | テーブル名（複数形 snake_case） | `users` |

## Entity テンプレート

> **注意**: Entity 名は Kotlin/Swift 標準ライブラリと衝突しないよう注意する。
> 例: `Collection` は `kotlin.collections.Collection` と衝突するため `import alias` が必要になる。
> 可能であれば `AppCollection` のように別名を使うか、`import ... as AppCollection` で回避する。

```kotlin
// domain/model/entity/{EntityName}.kt
package com.example.appmaster.domain.model.entity

import com.example.appmaster.domain.model.valueobject.{EntityName}Id
import kotlinx.datetime.Instant
import kotlinx.datetime.Clock

data class {EntityName}(
    val id: {EntityName}Id,
    val createdAt: Instant,
    val updatedAt: Instant
) {
    companion object {
        fun create(): {EntityName} = {EntityName}(
            id = {EntityName}Id.generate(),
            createdAt = Clock.System.now(),
            updatedAt = Clock.System.now()
        )
    }
}
```

## ValueObject テンプレート

```kotlin
// domain/model/valueobject/{EntityName}Id.kt
package com.example.appmaster.domain.model.valueobject

import java.util.UUID

@JvmInline
value class {EntityName}Id(val value: String) {
    companion object {
        fun generate(): {EntityName}Id = {EntityName}Id(UUID.randomUUID().toString())
    }
}
```

## Repository Interface テンプレート

```kotlin
// domain/repository/{EntityName}Repository.kt
package com.example.appmaster.domain.repository

import com.example.appmaster.domain.model.entity.{EntityName}
import com.example.appmaster.domain.model.valueobject.{EntityName}Id

interface {EntityName}Repository {
    suspend fun findById(id: {EntityName}Id): {EntityName}?
    suspend fun save(entity: {EntityName}): {EntityName}
    suspend fun delete(id: {EntityName}Id)
    suspend fun findAll(limit: Int = 100, offset: Int = 0): List<{EntityName}>
}
```

## UseCase テンプレート

```kotlin
// domain/usecase/Get{EntityName}UseCase.kt
package com.example.appmaster.domain.usecase

import com.example.appmaster.domain.error.DomainError
import com.example.appmaster.domain.model.entity.{EntityName}
import com.example.appmaster.domain.model.valueobject.{EntityName}Id
import com.example.appmaster.domain.repository.{EntityName}Repository

class Get{EntityName}UseCase(
    private val repository: {EntityName}Repository
) {
    suspend operator fun invoke(id: {EntityName}Id): Result<{EntityName}> {
        val entity = repository.findById(id)
            ?: return Result.failure(DomainError.{EntityName}NotFound(id))
        return Result.success(entity)
    }
}
```

## Exposed Table テンプレート

```kotlin
// data/entity/{EntityName}Table.kt
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.xTimestamp

object {EntityName}Table : Table("{table_name}") {
    val id = varchar("id", 36)
    val userId = varchar("user_id", 36).index()
        .references(UsersTable.id, onDelete = ReferenceOption.CASCADE)
    val name = varchar("name", 100)
    val createdAt = xTimestamp("created_at")

    override val primaryKey = PrimaryKey(id)
}
```

> **重要**: FK には必ず `onDelete = ReferenceOption.CASCADE` を指定する。

## 生成手順

1. Entity 名を決定（標準ライブラリとの名前衝突を確認）
2. テンプレート変数を置換
3. ファイルを適切なディレクトリに配置
4. FK に `onDelete = ReferenceOption.CASCADE` が含まれているか確認
5. DI Module に登録
6. テストファイルを生成
