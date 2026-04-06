# Auth Data 層リファレンス

認証に関する Data 層のコードテンプレートです。

## RefreshTokensTable.kt

```kotlin
package {{package}}.data.database

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp

/**
 * Refresh Token テーブル定義
 *
 * Token Rotation を実現するため、発行された Refresh Token を DB で管理
 */
object RefreshTokensTable : Table("refresh_tokens") {
    /** トークンID (UUID) */
    val id = varchar("id", 36)

    /** ユーザーID (FK) */
    val userId = varchar("user_id", 36).references(UsersTable.id)

    /** Refresh Token 値 (UUID) - ユニークインデックス */
    val token = varchar("token", 36).uniqueIndex()

    /** 有効期限 */
    val expiresAt = timestamp("expires_at")

    /** 作成日時 */
    val createdAt = timestamp("created_at")

    /** 無効化日時 (Token Rotation 時に設定) */
    val revokedAt = timestamp("revoked_at").nullable()

    override val primaryKey = PrimaryKey(id)
}
```

## AuthRepositoryImpl.kt

```kotlin
package {{package}}.data.repository

import at.favre.lib.crypto.bcrypt.BCrypt
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import {{package}}.data.database.DatabaseFactory.dbQuery
import {{package}}.data.database.RefreshTokensTable
import {{package}}.domain.model.AuthTokens
import {{package}}.domain.model.User
import {{package}}.domain.model.UserId
import {{package}}.domain.repository.AuthRepository
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import java.time.Instant
import java.util.*

/**
 * 認証リポジトリ実装
 *
 * @param jwtSecret JWT 署名用シークレット (32文字以上推奨)
 * @param jwtIssuer JWT 発行者
 * @param jwtAudience JWT 対象者
 * @param accessTokenExpiryMinutes Access Token 有効期限 (分)
 * @param refreshTokenExpiryDays Refresh Token 有効期限 (日)
 */
class AuthRepositoryImpl(
    private val jwtSecret: String,
    private val jwtIssuer: String,
    private val jwtAudience: String,
    private val accessTokenExpiryMinutes: Long = 15,
    private val refreshTokenExpiryDays: Long = 30
) : AuthRepository {

    private val bcryptHasher = BCrypt.withDefaults()
    private val bcryptVerifier = BCrypt.verifyer()

    override suspend fun generateTokens(user: User): AuthTokens {
        val now = System.currentTimeMillis()
        val accessTokenExpiry = Date(now + accessTokenExpiryMinutes * 60 * 1000)
        val refreshTokenExpiry = Instant.now().plusSeconds(refreshTokenExpiryDays * 24 * 60 * 60)

        // Access Token (JWT) 生成
        val accessToken = JWT.create()
            .withAudience(jwtAudience)
            .withIssuer(jwtIssuer)
            .withSubject(user.id.value)
            .withClaim("userId", user.id.value)
            .withClaim("email", user.email.value)
            .withIssuedAt(Date(now))
            .withExpiresAt(accessTokenExpiry)
            .withJWTId(UUID.randomUUID().toString())
            .sign(Algorithm.HMAC256(jwtSecret))

        // Refresh Token (UUID) 生成
        val refreshToken = UUID.randomUUID().toString()

        // Refresh Token を DB に保存
        dbQuery {
            RefreshTokensTable.insert {
                it[id] = UUID.randomUUID().toString()
                it[RefreshTokensTable.userId] = user.id.value
                it[token] = refreshToken
                it[expiresAt] = refreshTokenExpiry
                it[createdAt] = Instant.now()
                it[revokedAt] = null
            }
        }

        return AuthTokens(
            accessToken = accessToken,
            refreshToken = refreshToken
        )
    }

    override suspend fun validateRefreshToken(refreshToken: String): UserId? = dbQuery {
        val now = Instant.now()

        RefreshTokensTable
            .selectAll()
            .where {
                (RefreshTokensTable.token eq refreshToken) and
                (RefreshTokensTable.revokedAt.isNull()) and
                (RefreshTokensTable.expiresAt greater now)
            }
            .singleOrNull()
            ?.let { UserId(it[RefreshTokensTable.userId]) }
    }

    override suspend fun revokeRefreshToken(refreshToken: String): Unit = dbQuery {
        RefreshTokensTable.update({ RefreshTokensTable.token eq refreshToken }) {
            it[revokedAt] = Instant.now()
        }
    }

    override suspend fun revokeAllTokensForUser(userId: UserId): Unit = dbQuery {
        RefreshTokensTable.update({ RefreshTokensTable.userId eq userId.value }) {
            it[revokedAt] = Instant.now()
        }
    }

    override fun hashPassword(password: String): String {
        // コスト係数 12 (推奨: 10-14)
        // 高いほど安全だが処理時間も増加
        return bcryptHasher.hashToString(12, password.toCharArray())
    }

    override fun verifyPassword(password: String, hash: String): Boolean {
        return bcryptVerifier.verify(password.toCharArray(), hash).verified
    }
}
```

## DatabaseFactory への追加

`DatabaseFactory.kt` の `init` メソッドで `RefreshTokensTable` を追加:

```kotlin
object DatabaseFactory {
    fun init(environment: ApplicationEnvironment) {
        // ... 既存のコード ...

        transaction(database) {
            SchemaUtils.create(
                UsersTable,
                // ... 他のテーブル ...
                RefreshTokensTable  // ← 追加
            )
        }
    }
}
```

## よくある問題と解決法

### 1. Column 操作のインポート

Exposed で `greater`, `eq` などを使う場合:

```kotlin
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greater
```

### 2. where 句の書き方 (Exposed 0.40+)

```kotlin
// ✅ 正しい
.where { (column1 eq value1) and (column2 eq value2) }

// ❌ 古い書き方 (非推奨)
.select { column1 eq value1 }
```

### 3. nullable カラムのチェック

```kotlin
// revokedAt が null のレコードを検索
.where { RefreshTokensTable.revokedAt.isNull() }
```

### 4. Timestamp 型の使用

```kotlin
// build.gradle.kts
implementation("org.jetbrains.exposed:exposed-java-time:$exposed_version")

// Table 定義
import org.jetbrains.exposed.sql.javatime.timestamp

val createdAt = timestamp("created_at")
```
