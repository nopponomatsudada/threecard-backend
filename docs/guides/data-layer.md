# Data 層ガイド

## 概要

Data 層は永続化の詳細を担当します。Domain 層で定義された Repository インターフェースを実装します。

## 構成要素

### 1. Exposed Table 定義

```kotlin
// data/entity/UserTable.kt
object UserTable : Table("users") {
    val id = varchar("id", 36)
    val email = varchar("email", 255).uniqueIndex()
    val name = varchar("name", 100)
    val status = enumerationByName<UserStatus>("status", 20)
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")

    override val primaryKey = PrimaryKey(id)
}

// Result Row から Domain Entity へのマッピング
fun ResultRow.toUser(): User = User(
    id = UserId(this[UserTable.id]),
    email = Email.create(this[UserTable.email]).getOrThrow(),
    name = UserName.create(this[UserTable.name]).getOrThrow(),
    status = this[UserTable.status],
    createdAt = this[UserTable.createdAt],
    updatedAt = this[UserTable.updatedAt]
)
```

### 2. DAO (Data Access Object)

```kotlin
// data/dao/UserDao.kt
class UserDao {
    suspend fun findById(id: UserId): User? = dbQuery {
        UserTable.selectAll()
            .where { UserTable.id eq id.value }
            .map { it.toUser() }
            .singleOrNull()
    }

    suspend fun findByEmail(email: Email): User? = dbQuery {
        UserTable.selectAll()
            .where { UserTable.email eq email.value }
            .map { it.toUser() }
            .singleOrNull()
    }

    suspend fun insert(user: User): User = dbQuery {
        UserTable.insert {
            it[id] = user.id.value
            it[email] = user.email.value
            it[name] = user.name.value
            it[status] = user.status
            it[createdAt] = user.createdAt
            it[updatedAt] = user.updatedAt
        }
        user
    }

    suspend fun update(user: User): User = dbQuery {
        UserTable.update({ UserTable.id eq user.id.value }) {
            it[email] = user.email.value
            it[name] = user.name.value
            it[status] = user.status
            it[updatedAt] = user.updatedAt
        }
        user
    }

    suspend fun delete(id: UserId) = dbQuery {
        UserTable.deleteWhere { UserTable.id eq id.value }
    }

    suspend fun findAll(limit: Int, offset: Int): List<User> = dbQuery {
        UserTable.selectAll()
            .orderBy(UserTable.createdAt, SortOrder.DESC)
            .limit(limit, offset.toLong())  // ← 2引数形式を使用
            .map { row -> row.toUser() }
    }
}
```

### 3. Repository 実装

```kotlin
// data/repository/UserRepositoryImpl.kt
class UserRepositoryImpl(private val dao: UserDao) : UserRepository {
    override suspend fun findById(id: UserId): User? = dao.findById(id)

    override suspend fun findByEmail(email: Email): User? = dao.findByEmail(email)

    override suspend fun save(user: User): User {
        return if (dao.findById(user.id) != null) {
            dao.update(user)
        } else {
            dao.insert(user)
        }
    }

    override suspend fun delete(id: UserId) = dao.delete(id)

    override suspend fun findAll(limit: Int, offset: Int): List<User> =
        dao.findAll(limit, offset)
}
```

## データベース設定

### Database Plugin

```kotlin
// plugins/Database.kt
fun Application.configureDatabase() {
    val config = environment.config

    Database.connect(
        url = config.property("database.url").getString(),
        driver = config.property("database.driver").getString(),
        user = config.property("database.user").getString(),
        password = config.property("database.password").getString()
    )

    // マイグレーション実行
    transaction {
        SchemaUtils.createMissingTablesAndColumns(
            UserTable,
            // 他のテーブル...
        )
    }
}

// トランザクションヘルパー
suspend fun <T> dbQuery(block: suspend () -> T): T =
    newSuspendedTransaction(Dispatchers.IO) { block() }
```

### application.conf

```hocon
database {
    url = "jdbc:postgresql://localhost:5432/appmaster"
    url = ${?DATABASE_URL}
    driver = "org.postgresql.Driver"
    user = "postgres"
    user = ${?DATABASE_USER}
    password = "password"
    password = ${?DATABASE_PASSWORD}
}
```

## マッピング

### Domain ↔ Data 変換

```kotlin
// data/mapper/UserMapper.kt

// ResultRow → Domain Entity
fun ResultRow.toUser(): User = User(
    id = UserId(this[UserTable.id]),
    email = Email.create(this[UserTable.email]).getOrThrow(),
    name = UserName.create(this[UserTable.name]).getOrThrow(),
    status = this[UserTable.status],
    createdAt = this[UserTable.createdAt],
    updatedAt = this[UserTable.updatedAt]
)

// Domain Entity → Insert Statement
fun User.toInsertStatement(): UserTable.(InsertStatement<Number>) -> Unit = {
    it[id] = this@toInsertStatement.id.value
    it[email] = this@toInsertStatement.email.value
    it[name] = this@toInsertStatement.name.value
    it[status] = this@toInsertStatement.status
    it[createdAt] = this@toInsertStatement.createdAt
    it[updatedAt] = this@toInsertStatement.updatedAt
}
```

## トランザクション管理

### 基本パターン

```kotlin
suspend fun <T> dbQuery(block: suspend () -> T): T =
    newSuspendedTransaction(Dispatchers.IO) { block() }
```

### 複数操作のトランザクション

```kotlin
class TransferUseCase(
    private val fromRepository: AccountRepository,
    private val toRepository: AccountRepository
) {
    suspend operator fun invoke(fromId: AccountId, toId: AccountId, amount: Money): Result<Unit> {
        return dbQuery {
            val from = fromRepository.findById(fromId)
                ?: return@dbQuery Result.failure(DomainError.AccountNotFound(fromId))
            val to = toRepository.findById(toId)
                ?: return@dbQuery Result.failure(DomainError.AccountNotFound(toId))

            fromRepository.save(from.withdraw(amount))
            toRepository.save(to.deposit(amount))

            Result.success(Unit)
        }
    }
}
```

## テスト

### テスト用データベース設定

```kotlin
// test/TestDatabase.kt
object TestDatabase {
    fun setup() {
        Database.connect(
            url = "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver"
        )
        transaction {
            SchemaUtils.create(UserTable)
        }
    }

    fun teardown() {
        transaction {
            SchemaUtils.drop(UserTable)
        }
    }
}
```

### Repository テスト

```kotlin
class UserRepositoryImplTest : FunSpec({
    beforeTest { TestDatabase.setup() }
    afterTest { TestDatabase.teardown() }

    test("should save and retrieve user") {
        val repository = UserRepositoryImpl(UserDao())
        val user = User.create(
            email = Email.create("test@example.com").getOrThrow(),
            name = UserName.create("Test").getOrThrow()
        )

        repository.save(user)
        val retrieved = repository.findById(user.id)

        retrieved shouldBe user
    }
})
```

## エラーハンドリング

### データベースエラーの変換

```kotlin
class UserRepositoryImpl(private val dao: UserDao) : UserRepository {
    override suspend fun save(user: User): User {
        return try {
            if (dao.findById(user.id) != null) {
                dao.update(user)
            } else {
                dao.insert(user)
            }
        } catch (e: ExposedSQLException) {
            when {
                e.message?.contains("unique constraint") == true ->
                    throw DomainError.EmailAlreadyExists(user.email)
                else -> throw e
            }
        }
    }
}
```

## よくある問題と解決法

### 1. カラム値の増減（インクリメント/デクリメント）

#### 問題

```kotlin
// ❌ エラー: Unresolved reference
UserTable.update({ UserTable.id eq userId.value }) {
    it[likeCount] = likeCount + 1  // コンパイルエラー
}
```

#### 解決策

```kotlin
// ✅ 正しい書き方: テーブル名で修飾 + SqlExpressionBuilder をインポート
import org.jetbrains.exposed.sql.SqlExpressionBuilder.plus
import org.jetbrains.exposed.sql.SqlExpressionBuilder.minus

UserTable.update({ UserTable.id eq userId.value }) {
    it[likeCount] = UserTable.likeCount + 1  // テーブル名.カラム名
}

// デクリメントの場合
UserTable.update({ UserTable.id eq userId.value }) {
    it[likeCount] = UserTable.likeCount - 1
}
```

### 2. ページネーション（limit/offset）

#### 問題

```kotlin
// ❌ Exposed 0.40+ では問題が発生する可能性あり
UserTable.selectAll()
    .limit(pageSize)
    .offset(offset)  // チェーンが正しく動作しない場合がある
    .map { it.toUser() }  // Unresolved reference: it
```

#### 解決策

```kotlin
// ✅ 2引数形式を使用
UserTable.selectAll()
    .orderBy(UserTable.createdAt, SortOrder.DESC)
    .limit(pageSize, offset)  // limit(n, offset) の形式
    .map { row -> row.toUser() }  // 明示的なパラメータ名を使用
```

### 3. 必須インポート

Exposed を使用する際の推奨インポート:

```kotlin
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.plus
import org.jetbrains.exposed.sql.SqlExpressionBuilder.minus
import java.time.Instant
```
