# Auth Domain 層リファレンス

認証に関する Domain 層のコードテンプレートです。

## AuthTokens.kt

```kotlin
package {{package}}.domain.model

/**
 * 認証トークンのペア
 *
 * @property accessToken API リクエストに使用する JWT
 * @property refreshToken Access Token 更新用のトークン
 */
data class AuthTokens(
    val accessToken: String,
    val refreshToken: String
)
```

## AuthRepository.kt

```kotlin
package {{package}}.domain.repository

import {{package}}.domain.model.AuthTokens
import {{package}}.domain.model.User
import {{package}}.domain.model.UserId

/**
 * 認証リポジトリインターフェース
 *
 * JWT トークンの生成・検証とパスワードのハッシュ化を担当
 */
interface AuthRepository {
    /**
     * Access Token と Refresh Token を生成
     *
     * @param user トークンを発行するユーザー
     * @return 生成されたトークンペア
     */
    suspend fun generateTokens(user: User): AuthTokens

    /**
     * Refresh Token を検証し、対応するユーザーIDを返す
     *
     * @param refreshToken 検証するトークン
     * @return 有効な場合はユーザーID、無効な場合は null
     */
    suspend fun validateRefreshToken(refreshToken: String): UserId?

    /**
     * Refresh Token を無効化
     *
     * Token Rotation の一環として、使用済みトークンを無効化
     *
     * @param refreshToken 無効化するトークン
     */
    suspend fun revokeRefreshToken(refreshToken: String)

    /**
     * ユーザーの全 Refresh Token を無効化
     *
     * ログアウト時やセキュリティ上の理由で全セッションを終了する場合に使用
     *
     * @param userId 対象ユーザーのID
     */
    suspend fun revokeAllTokensForUser(userId: UserId)

    /**
     * パスワードをハッシュ化
     *
     * BCrypt を使用してパスワードをハッシュ化
     *
     * @param password 平文パスワード
     * @return ハッシュ化されたパスワード
     */
    fun hashPassword(password: String): String

    /**
     * パスワードを検証
     *
     * @param password 平文パスワード
     * @param hash ハッシュ化されたパスワード
     * @return 一致する場合は true
     */
    fun verifyPassword(password: String, hash: String): Boolean
}
```

## DomainError への追加

既存の `DomainError.kt` に以下を追加:

```kotlin
sealed class DomainError : Exception() {
    // ... 既存のエラー ...

    sealed class Unauthorized : DomainError() {
        data class InvalidCredentials(
            override val message: String = "Invalid email or password"
        ) : Unauthorized()

        data class InvalidToken(
            override val message: String = "Invalid or expired token"
        ) : Unauthorized()

        data class TokenExpired(
            override val message: String = "Token has expired"
        ) : Unauthorized()

        companion object {
            fun invalidCredentials() = InvalidCredentials()
            fun invalidToken() = InvalidToken()
            fun tokenExpired() = TokenExpired()
        }
    }

    sealed class Conflict : DomainError() {
        data class EmailExists(
            override val message: String = "Email already registered"
        ) : Conflict()

        data class UsernameExists(
            override val message: String = "Username already taken"
        ) : Conflict()

        companion object {
            fun emailExists() = EmailExists()
            fun usernameExists() = UsernameExists()
        }
    }

    sealed class Validation : DomainError() {
        data class PasswordTooShort(
            override val message: String = "Password must be at least 8 characters"
        ) : Validation()

        data class InvalidEmailFormat(
            override val message: String = "Invalid email format"
        ) : Validation()

        companion object {
            fun passwordTooShort() = PasswordTooShort()
            fun invalidEmailFormat() = InvalidEmailFormat()
        }
    }
}
```

## User モデルへの追加

`User.kt` に `passwordHash` フィールドが必要:

```kotlin
data class User(
    val id: UserId,
    val email: Email,
    val username: Username,
    val displayName: String,
    val bio: String? = null,
    val avatarUrl: String? = null,
    val passwordHash: String,  // ← 追加
    val followerCount: Int = 0,
    val followingCount: Int = 0,
    val isVerified: Boolean = false,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now()
)
```

## UserRepository への追加

`UserRepository.kt` に以下のメソッドが必要:

```kotlin
interface UserRepository {
    // ... 既存のメソッド ...

    /**
     * メールアドレスでユーザーを検索
     */
    suspend fun findByEmail(email: String): User?

    /**
     * ユーザー名でユーザーを検索
     */
    suspend fun findByUsername(username: String): User?

    /**
     * ユーザーを保存（新規作成）
     */
    suspend fun save(user: User): User
}
```
