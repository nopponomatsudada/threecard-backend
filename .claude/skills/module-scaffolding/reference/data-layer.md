# Data Layer Reference

## 必須インポート

> **注意**: Exposed 1.x ではパッケージが変更されている。
> `exposed.sql.*` → `exposed.v1.core.*`（DDL/型定義）/ `exposed.v1.jdbc.*`（DML: insert, selectAll, update, deleteWhere）
> `newSuspendedTransaction` → `suspendTransaction`（`v1.jdbc.transactions`）
> `timestamp()` → `xTimestamp()`（kotlinx.datetime.Instant を返す場合）

```kotlin
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.datetime.xTimestamp
```

## Table 定義

> **重要**: FK には必ず `onDelete = ReferenceOption.CASCADE` を指定する。
> 省略するとユーザー削除時に FK 制約違反（500 エラー）が発生する。

```kotlin
object EntityTable : Table("entities") {
    val id = varchar("id", 36)
    val userId = varchar("user_id", 36).references(UserTable.id, onDelete = ReferenceOption.CASCADE)
    val name = varchar("name", 100)
    val status = enumerationByName<EntityStatus>("status", 20)
    val createdAt = xTimestamp("created_at")
    val updatedAt = xTimestamp("updated_at")

    override val primaryKey = PrimaryKey(id)

    init {
        // インデックス
        index(false, userId)
        index(false, status)
        uniqueIndex(name)
    }
}
```

## Mapper

```kotlin
// ResultRow → Domain Entity
fun ResultRow.toEntity(): Entity = Entity(
    id = EntityId(this[EntityTable.id]),
    name = EntityName.create(this[EntityTable.name]).getOrThrow(),
    status = this[EntityTable.status],
    createdAt = this[EntityTable.createdAt],
    updatedAt = this[EntityTable.updatedAt]
)
```

## DAO パターン

```kotlin
class EntityDao {
    suspend fun findById(id: EntityId): Entity? = dbQuery {
        EntityTable.selectAll()
            .where { EntityTable.id eq id.value }
            .map { it.toEntity() }
            .singleOrNull()
    }

    suspend fun insert(entity: Entity): Entity = dbQuery {
        EntityTable.insert {
            it[id] = entity.id.value
            it[name] = entity.name.value
            it[status] = entity.status
            it[createdAt] = entity.createdAt
            it[updatedAt] = entity.updatedAt
        }
        entity
    }

    suspend fun update(entity: Entity): Entity = dbQuery {
        EntityTable.update({ EntityTable.id eq entity.id.value }) {
            it[name] = entity.name.value
            it[status] = entity.status
            it[updatedAt] = entity.updatedAt
        }
        entity
    }

    // 注意: deleteWhere は Int を返す。override fun delete(): Unit の場合は末尾に Unit を明記
    suspend fun delete(id: EntityId): Unit = dbQuery {
        EntityTable.deleteWhere { EntityTable.id eq id.value }
        Unit
    }
}
```

## サブリソース DELETE のオーナーシップ検証パターン

サブリソース（例: Section, Item）の DELETE では、
サブリソース → 親リソースの所有者を検証する必要がある。

```kotlin
// Repository に findXxxById ヘルパーを追加
override suspend fun findSectionById(sectionId: String): Section? = dbQuery {
    SectionsTable.selectAll()
        .where { SectionsTable.id eq sectionId }
        .singleOrNull()?.let { toSection(it) }
}

// Routes でオーナーシップチェーンを実装
delete("/api/v1/sections/{sectionId}") {
    val section = repository.findSectionById(sectionId)
        ?: throw DomainException(DomainError.NotFound("Section"))
    val parent = repository.findById(section.parentId)
        ?: throw DomainException(DomainError.NotFound("ParentResource"))
    if (parent.userId != user.id) throw DomainException(DomainError.Forbidden)
    repository.deleteSection(sectionId)
}
```

## Repository 実装

```kotlin
class EntityRepositoryImpl(
    private val dao: EntityDao
) : EntityRepository {

    override suspend fun findById(id: EntityId): Entity? =
        dao.findById(id)

    override suspend fun save(entity: Entity): Entity {
        return if (dao.findById(entity.id) != null) {
            dao.update(entity)
        } else {
            dao.insert(entity)
        }
    }

    override suspend fun delete(id: EntityId) =
        dao.delete(id)
}
```

## トランザクション

> Exposed 1.x では `newSuspendedTransaction` → `suspendTransaction` に変更。
> Dispatchers.IO パラメータは不要。

```kotlin
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction

suspend fun <T> dbQuery(block: suspend () -> T): T =
    suspendTransaction { block() }
```

## カウント更新パターン

```kotlin
// ✅ いいね数の増加（テーブル名でカラムを修飾）
EntityTable.update({ EntityTable.id eq entityId.value }) {
    it[likeCount] = EntityTable.likeCount + 1
}

// ✅ いいね数の減少
EntityTable.update({ EntityTable.id eq entityId.value }) {
    it[likeCount] = EntityTable.likeCount - 1
}
```

## ページネーション

```kotlin
// ✅ limit(n, offset) の2引数形式を使用
EntityTable.selectAll()
    .orderBy(EntityTable.createdAt, SortOrder.DESC)
    .limit(pageSize, offset.toLong())
    .map { row -> row.toEntity() }
```
