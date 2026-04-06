# Auth UseCase 層リファレンス

認証に関する UseCase 層のコードテンプレートです。

## RegisterUseCase.kt

```kotlin
package {{package}}.domain.usecase.auth

import {{package}}.domain.error.DomainError
import {{package}}.domain.model.*
import {{package}}.domain.repository.AuthRepository
import {{package}}.domain.repository.UserRepository
import java.util.UUID

/**
 * ユーザー登録ユースケース
 *
 * 新規ユーザーを作成し、認証トークンを発行する
 */
class RegisterUseCase(
    private val userRepository: UserRepository,
    private val authRepository: AuthRepository
) {
    data class Input(
        val email: Email,
        val username: Username,
        val password: String,
        val displayName: String? = null
    )

    data class Output(
        val accessToken: String,
        val refreshToken: String,
        val user: User
    )

    suspend operator fun invoke(input: Input): Output {
        // パスワード検証 (8文字以上)
        if (input.password.length < 8) {
            throw DomainError.Validation.passwordTooShort()
        }

        // メールアドレス重複チェック
        if (userRepository.findByEmail(input.email.value) != null) {
            throw DomainError.Conflict.emailExists()
        }

        // ユーザー名重複チェック
        if (userRepository.findByUsername(input.username.value) != null) {
            throw DomainError.Conflict.usernameExists()
        }

        // ユーザー作成
        val user = User(
            id = UserId(UUID.randomUUID().toString()),
            email = input.email,
            username = input.username,
            displayName = input.displayName ?: input.username.value,
            passwordHash = authRepository.hashPassword(input.password)
        )

        val savedUser = userRepository.save(user)
        val tokens = authRepository.generateTokens(savedUser)

        return Output(
            accessToken = tokens.accessToken,
            refreshToken = tokens.refreshToken,
            user = savedUser
        )
    }
}
```

## LoginUseCase.kt

```kotlin
package {{package}}.domain.usecase.auth

import {{package}}.domain.error.DomainError
import {{package}}.domain.model.User
import {{package}}.domain.repository.AuthRepository
import {{package}}.domain.repository.UserRepository

/**
 * ログインユースケース
 *
 * メールアドレスとパスワードでユーザーを認証し、トークンを発行する
 */
class LoginUseCase(
    private val userRepository: UserRepository,
    private val authRepository: AuthRepository
) {
    data class Input(
        val email: String,
        val password: String
    )

    data class Output(
        val accessToken: String,
        val refreshToken: String,
        val user: User
    )

    suspend operator fun invoke(input: Input): Output {
        // ユーザー検索
        val user = userRepository.findByEmail(input.email)
            ?: throw DomainError.Unauthorized.invalidCredentials()

        // パスワード検証
        if (!authRepository.verifyPassword(input.password, user.passwordHash)) {
            throw DomainError.Unauthorized.invalidCredentials()
        }

        // トークン生成
        val tokens = authRepository.generateTokens(user)

        return Output(
            accessToken = tokens.accessToken,
            refreshToken = tokens.refreshToken,
            user = user
        )
    }
}
```

## RefreshTokenUseCase.kt

```kotlin
package {{package}}.domain.usecase.auth

import {{package}}.domain.error.DomainError
import {{package}}.domain.model.User
import {{package}}.domain.repository.AuthRepository
import {{package}}.domain.repository.UserRepository

/**
 * トークン更新ユースケース
 *
 * Refresh Token を使用して新しい Access Token を発行する
 * Token Rotation: 古い Refresh Token は無効化される
 */
class RefreshTokenUseCase(
    private val userRepository: UserRepository,
    private val authRepository: AuthRepository
) {
    data class Output(
        val accessToken: String,
        val refreshToken: String,
        val user: User
    )

    suspend operator fun invoke(refreshToken: String): Output {
        // Refresh Token 検証
        val userId = authRepository.validateRefreshToken(refreshToken)
            ?: throw DomainError.Unauthorized.invalidToken()

        // ユーザー取得
        val user = userRepository.findById(userId)
            ?: throw DomainError.NotFound.user(userId.value)

        // 古いトークンを無効化（Token Rotation）
        // セキュリティ: トークン再利用攻撃を防止
        authRepository.revokeRefreshToken(refreshToken)

        // 新しいトークンを生成
        val tokens = authRepository.generateTokens(user)

        return Output(
            accessToken = tokens.accessToken,
            refreshToken = tokens.refreshToken,
            user = user
        )
    }
}
```

## LogoutUseCase.kt

```kotlin
package {{package}}.domain.usecase.auth

import {{package}}.domain.model.UserId
import {{package}}.domain.repository.AuthRepository

/**
 * ログアウトユースケース
 *
 * ユーザーの全 Refresh Token を無効化し、全セッションを終了する
 */
class LogoutUseCase(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(userId: UserId) {
        // 該当ユーザーの全 Refresh Token を無効化
        authRepository.revokeAllTokensForUser(userId)
    }
}
```

## GetCurrentUserUseCase.kt (オプション)

```kotlin
package {{package}}.domain.usecase.auth

import {{package}}.domain.error.DomainError
import {{package}}.domain.model.User
import {{package}}.domain.model.UserId
import {{package}}.domain.repository.UserRepository

/**
 * 現在のユーザー取得ユースケース
 *
 * JWT から取得した userId で現在のユーザー情報を取得
 */
class GetCurrentUserUseCase(
    private val userRepository: UserRepository
) {
    suspend operator fun invoke(userId: UserId): User {
        return userRepository.findById(userId)
            ?: throw DomainError.NotFound.user(userId.value)
    }
}
```

## ChangePasswordUseCase.kt (オプション)

```kotlin
package {{package}}.domain.usecase.auth

import {{package}}.domain.error.DomainError
import {{package}}.domain.model.UserId
import {{package}}.domain.repository.AuthRepository
import {{package}}.domain.repository.UserRepository

/**
 * パスワード変更ユースケース
 */
class ChangePasswordUseCase(
    private val userRepository: UserRepository,
    private val authRepository: AuthRepository
) {
    data class Input(
        val userId: UserId,
        val currentPassword: String,
        val newPassword: String
    )

    suspend operator fun invoke(input: Input) {
        // ユーザー取得
        val user = userRepository.findById(input.userId)
            ?: throw DomainError.NotFound.user(input.userId.value)

        // 現在のパスワードを検証
        if (!authRepository.verifyPassword(input.currentPassword, user.passwordHash)) {
            throw DomainError.Unauthorized.invalidCredentials()
        }

        // 新しいパスワードの検証
        if (input.newPassword.length < 8) {
            throw DomainError.Validation.passwordTooShort()
        }

        // パスワード更新
        val newPasswordHash = authRepository.hashPassword(input.newPassword)
        userRepository.updatePassword(input.userId, newPasswordHash)

        // 全トークンを無効化（セキュリティのため再ログインを要求）
        authRepository.revokeAllTokensForUser(input.userId)
    }
}
```

## UseCase 設計のポイント

### 1. 単一責任

各 UseCase は1つの機能のみを担当:

- `RegisterUseCase` → ユーザー登録
- `LoginUseCase` → ログイン
- `RefreshTokenUseCase` → トークン更新
- `LogoutUseCase` → ログアウト

### 2. Input/Output パターン

- `Input`: リクエストパラメータをまとめる
- `Output`: レスポンスデータをまとめる
- Domain 層のモデルを使用（DTO は使わない）

### 3. ビジネスルールの配置

バリデーションやビジネスルールは UseCase に配置:

```kotlin
// ✅ UseCase でバリデーション
if (input.password.length < 8) {
    throw DomainError.Validation.passwordTooShort()
}

// ❌ Repository や Routes でバリデーションしない
```

### 4. エラーハンドリング

ドメインエラーを throw:

```kotlin
throw DomainError.Unauthorized.invalidCredentials()
throw DomainError.Conflict.emailExists()
throw DomainError.Validation.passwordTooShort()
```

Routes 層でこれらを HTTP ステータスコードに変換する。
