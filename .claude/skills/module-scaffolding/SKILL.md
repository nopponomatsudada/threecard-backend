---
name: module-scaffolding
description: >
  新規機能の実装ガイド。Domain → Data → Routes の順に Clean Architecture に従った
  実装をステップバイステップで支援する。
  トリガー: "新しい機能", "エンティティを追加", "API追加", "モジュール作成"
---

# Module Scaffolding

新規機能を Clean Architecture に従って実装するガイド。

## 実装順序

```
Step 1: Domain 層
    ↓
Step 2: Data 層
    ↓
Step 3: Routes 層
    ↓
Step 4: DI 設定
    ↓
Step 5: テスト作成
```

## Step 1: Domain 層

### 1.1 Entity 作成

```kotlin
// domain/model/entity/{EntityName}.kt
data class {EntityName}(
    val id: {EntityName}Id,
    // 他のプロパティ
    val createdAt: Instant,
    val updatedAt: Instant
) {
    companion object {
        fun create(/* params */): {EntityName} = {EntityName}(
            id = {EntityName}Id.generate(),
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
    }
}
```

### 1.2 ValueObject 作成

```kotlin
// domain/model/valueobject/{EntityName}Id.kt
@JvmInline
value class {EntityName}Id(val value: String) {
    companion object {
        fun generate(): {EntityName}Id = {EntityName}Id(UUID.randomUUID().toString())
    }
}
```

### 1.3 Repository インターフェース

```kotlin
// domain/repository/{EntityName}Repository.kt
interface {EntityName}Repository {
    suspend fun findById(id: {EntityName}Id): {EntityName}?
    suspend fun save(entity: {EntityName}): {EntityName}
    suspend fun delete(id: {EntityName}Id)
}
```

### 1.4 UseCase 作成

```kotlin
// domain/usecase/Get{EntityName}UseCase.kt
class Get{EntityName}UseCase(private val repository: {EntityName}Repository) {
    suspend operator fun invoke(id: {EntityName}Id): Result<{EntityName}> {
        val entity = repository.findById(id)
            ?: return Result.failure(DomainError.{EntityName}NotFound(id))
        return Result.success(entity)
    }
}
```

### 1.5 DomainError 追加

```kotlin
// domain/error/DomainError.kt に追加
data class {EntityName}NotFound(val id: {EntityName}Id) : DomainError()
```

## Step 2: Data 層

### 2.1 Table 定義

```kotlin
// data/entity/{EntityName}Table.kt
object {EntityName}Table : Table("{table_name}") {
    val id = varchar("id", 36)
    // 他のカラム
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")

    override val primaryKey = PrimaryKey(id)
}
```

### 2.2 DAO 実装

```kotlin
// data/dao/{EntityName}Dao.kt
class {EntityName}Dao {
    suspend fun findById(id: {EntityName}Id): {EntityName}? = dbQuery {
        {EntityName}Table.selectAll()
            .where { {EntityName}Table.id eq id.value }
            .map { it.to{EntityName}() }
            .singleOrNull()
    }
    // 他のメソッド
}
```

### 2.3 Repository 実装

```kotlin
// data/repository/{EntityName}RepositoryImpl.kt
class {EntityName}RepositoryImpl(
    private val dao: {EntityName}Dao
) : {EntityName}Repository {
    override suspend fun findById(id: {EntityName}Id) = dao.findById(id)
    // 他のメソッド
}
```

## Step 3: Routes 層

### 3.1 DTO 定義

```kotlin
// routes/dto/request/Create{EntityName}Request.kt
@Serializable
data class Create{EntityName}Request(/* fields */)

// routes/dto/response/{EntityName}Response.kt
@Serializable
data class {EntityName}Response(/* fields */) {
    companion object {
        fun from(entity: {EntityName}): {EntityName}Response = /* mapping */
    }
}
```

### 3.2 Route 定義

```kotlin
// routes/{EntityName}Routes.kt
fun Route.{entityName}Routes() {
    val getUseCase by inject<Get{EntityName}UseCase>()
    val createUseCase by inject<Create{EntityName}UseCase>()

    route("/api/v1/{entityName}s") {
        get("/{id}") { /* ... */ }
        post { /* ... */ }
    }
}
```

## Step 4: DI 設定

```kotlin
// plugins/Koin.kt
val {entityName}Module = module {
    single { {EntityName}Dao() }
    single<{EntityName}Repository> { {EntityName}RepositoryImpl(get()) }
    single { Get{EntityName}UseCase(get()) }
    single { Create{EntityName}UseCase(get()) }
}
```

## Step 5: テスト

- [ ] Entity/ValueObject の Unit Test
- [ ] UseCase の Unit Test
- [ ] Repository の Integration Test
- [ ] Routes の API Test

## Reference

- [Domain Layer Guide](reference/domain-layer.md)
- [Data Layer Guide](reference/data-layer.md)
- [Routes Layer Guide](reference/routes-layer.md)
