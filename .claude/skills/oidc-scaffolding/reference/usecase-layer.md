# OIDC UseCase 層リファレンス

OIDC 認証に関する UseCase 層のコードテンプレートです。

## InitiateOIDCUseCase.kt

```kotlin
package {{package}}.domain.usecase.oidc

import {{package}}.domain.model.OIDCProvider
import {{package}}.domain.repository.OIDCRepository

/**
 * OIDC 認証開始
 *
 * 認証 URL と state を生成
 */
class InitiateOIDCUseCase(
    private val oidcRepository: OIDCRepository
) {
    data class Input(
        val provider: OIDCProvider,
        val codeChallenge: String,
        val redirectUri: String,
        val nonce: String? = null
    )

    data class Output(
        val authUrl: String,
        val state: String
    )

    suspend operator fun invoke(input: Input): Output {
        // State を生成・保存
        val oidcState = oidcRepository.createState(
            provider = input.provider,
            codeChallenge = input.codeChallenge,
            redirectUri = input.redirectUri,
            nonce = input.nonce
        )

        // 認証 URL を構築
        val authUrl = oidcRepository.buildAuthorizationUrl(
            provider = input.provider,
            state = oidcState.state,
            codeChallenge = input.codeChallenge,
            redirectUri = input.redirectUri,
            nonce = input.nonce
        )

        return Output(
            authUrl = authUrl,
            state = oidcState.state
        )
    }
}
```

## OIDCCallbackUseCase.kt

```kotlin
package {{package}}.domain.usecase.oidc

import {{package}}.domain.error.DomainError
import {{package}}.domain.model.*
import {{package}}.domain.repository.AuthRepository
import {{package}}.domain.repository.OIDCRepository
import {{package}}.domain.repository.UserRepository
import java.util.UUID

/**
 * OIDC コールバック処理
 *
 * 認可コードを受け取り、トークン交換とユーザー認証を行う
 * 同じメールアドレスの既存ユーザーがいる場合は自動的にリンク
 */
class OIDCCallbackUseCase(
    private val oidcRepository: OIDCRepository,
    private val userRepository: UserRepository,
    private val authRepository: AuthRepository
) {
    data class Input(
        val provider: OIDCProvider,
        val code: String,
        val state: String,
        val codeVerifier: String
    )

    data class Output(
        val accessToken: String,
        val refreshToken: String,
        val user: User,
        val isNewUser: Boolean
    )

    suspend operator fun invoke(input: Input): Output {
        // 1. State 検証（CSRF 対策）
        val storedState = oidcRepository.validateAndConsumeState(input.state)
            ?: throw DomainError.OIDC.invalidState()

        if (storedState.provider != input.provider) {
            throw DomainError.OIDC.invalidState()
        }

        // 2. 認可コードをトークンに交換
        val tokenResponse = oidcRepository.exchangeCodeForTokens(
            provider = input.provider,
            code = input.code,
            codeVerifier = input.codeVerifier,
            redirectUri = storedState.redirectUri
        )

        // 3. ID Token 検証
        val claims = oidcRepository.validateIdToken(
            provider = input.provider,
            idToken = tokenResponse.idToken,
            nonce = storedState.nonce
        )

        // 4. 既存の OIDC アカウントを検索
        val existingOIDCAccount = oidcRepository.findByProviderAndProviderUserId(
            provider = input.provider,
            providerUserId = claims.subject
        )

        // 5. ユーザー取得または作成
        val (user, isNewUser) = if (existingOIDCAccount != null) {
            // 既存の OIDC リンクがある → そのユーザーでログイン
            val user = userRepository.findById(existingOIDCAccount.userId)
                ?: throw DomainError.NotFound.user(existingOIDCAccount.userId.value)
            user to false
        } else {
            // 新規 OIDC ログイン
            resolveOrCreateUser(claims, input.provider)
        }

        // 6. OIDC アカウントが未作成なら作成
        if (existingOIDCAccount == null) {
            val oidcAccount = OIDCAccount(
                id = OIDCAccountId(UUID.randomUUID().toString()),
                userId = user.id,
                provider = input.provider,
                providerUserId = claims.subject,
                email = claims.email,
                name = claims.name,
                avatarUrl = claims.picture
            )
            oidcRepository.save(oidcAccount)
        }

        // 7. JWT トークン発行
        val tokens = authRepository.generateTokens(user)

        return Output(
            accessToken = tokens.accessToken,
            refreshToken = tokens.refreshToken,
            user = user,
            isNewUser = isNewUser
        )
    }

    /**
     * メールアドレスで既存ユーザーを探し、いなければ新規作成
     * 同じメールの既存ユーザーがいれば自動リンク
     */
    private suspend fun resolveOrCreateUser(
        claims: OIDCIdTokenClaims,
        provider: OIDCProvider
    ): Pair<User, Boolean> {
        // メールアドレスで既存ユーザーを検索
        val email = claims.email
        if (email != null) {
            val existingUser = userRepository.findByEmail(email)
            if (existingUser != null) {
                // 既存ユーザーに自動リンク
                return existingUser to false
            }
        }

        // 新規ユーザー作成
        val newUser = User(
            id = UserId(UUID.randomUUID().toString()),
            email = email?.let { Email(it) },
            username = generateUsername(claims, provider),
            displayName = claims.name ?: "User",
            avatarUrl = claims.picture,
            passwordHash = null, // OIDC ユーザーはパスワードなし
            emailVerified = claims.emailVerified ?: false
        )

        val savedUser = userRepository.save(newUser)
        return savedUser to true
    }

    private fun generateUsername(claims: OIDCIdTokenClaims, provider: OIDCProvider): Username {
        val base = claims.name?.replace(" ", "")?.lowercase()
            ?: "${provider.id}_${claims.subject.take(8)}"
        return Username("${base}_${UUID.randomUUID().toString().take(4)}")
    }
}
```

## ExchangeProviderTokenUseCase.kt

```kotlin
package {{package}}.domain.usecase.oidc

import {{package}}.domain.error.DomainError
import {{package}}.domain.model.*
import {{package}}.domain.repository.AuthRepository
import {{package}}.domain.repository.OIDCRepository
import {{package}}.domain.repository.UserRepository
import java.util.UUID

/**
 * ネイティブ SDK トークン交換
 *
 * Google Sign-In SDK や Sign in with Apple SDK から取得した
 * ID Token を検証し、バックエンドの JWT を発行
 */
class ExchangeProviderTokenUseCase(
    private val oidcRepository: OIDCRepository,
    private val userRepository: UserRepository,
    private val authRepository: AuthRepository
) {
    data class Input(
        val provider: OIDCProvider,
        val idToken: String,
        val nonce: String? = null
    )

    data class Output(
        val accessToken: String,
        val refreshToken: String,
        val user: User,
        val isNewUser: Boolean
    )

    suspend operator fun invoke(input: Input): Output {
        // 1. ID Token 検証
        val claims = oidcRepository.validateIdToken(
            provider = input.provider,
            idToken = input.idToken,
            nonce = input.nonce
        )

        // 2. 既存の OIDC アカウントを検索
        val existingOIDCAccount = oidcRepository.findByProviderAndProviderUserId(
            provider = input.provider,
            providerUserId = claims.subject
        )

        // 3. ユーザー取得または作成
        val (user, isNewUser) = if (existingOIDCAccount != null) {
            val user = userRepository.findById(existingOIDCAccount.userId)
                ?: throw DomainError.NotFound.user(existingOIDCAccount.userId.value)
            user to false
        } else {
            resolveOrCreateUser(claims, input.provider)
        }

        // 4. OIDC アカウントが未作成なら作成
        if (existingOIDCAccount == null) {
            val oidcAccount = OIDCAccount(
                id = OIDCAccountId(UUID.randomUUID().toString()),
                userId = user.id,
                provider = input.provider,
                providerUserId = claims.subject,
                email = claims.email,
                name = claims.name,
                avatarUrl = claims.picture
            )
            oidcRepository.save(oidcAccount)
        }

        // 5. JWT トークン発行
        val tokens = authRepository.generateTokens(user)

        return Output(
            accessToken = tokens.accessToken,
            refreshToken = tokens.refreshToken,
            user = user,
            isNewUser = isNewUser
        )
    }

    private suspend fun resolveOrCreateUser(
        claims: OIDCIdTokenClaims,
        provider: OIDCProvider
    ): Pair<User, Boolean> {
        val email = claims.email
        if (email != null) {
            val existingUser = userRepository.findByEmail(email)
            if (existingUser != null) {
                return existingUser to false
            }
        }

        val newUser = User(
            id = UserId(UUID.randomUUID().toString()),
            email = email?.let { Email(it) },
            username = generateUsername(claims, provider),
            displayName = claims.name ?: "User",
            avatarUrl = claims.picture,
            passwordHash = null,
            emailVerified = claims.emailVerified ?: false
        )

        val savedUser = userRepository.save(newUser)
        return savedUser to true
    }

    private fun generateUsername(claims: OIDCIdTokenClaims, provider: OIDCProvider): Username {
        val base = claims.name?.replace(" ", "")?.lowercase()
            ?: "${provider.id}_${claims.subject.take(8)}"
        return Username("${base}_${UUID.randomUUID().toString().take(4)}")
    }
}
```

## LinkOIDCProviderUseCase.kt

```kotlin
package {{package}}.domain.usecase.oidc

import {{package}}.domain.error.DomainError
import {{package}}.domain.model.*
import {{package}}.domain.repository.OIDCRepository
import {{package}}.domain.repository.UserRepository
import java.util.UUID

/**
 * 既存アカウントに OIDC プロバイダーをリンク
 */
class LinkOIDCProviderUseCase(
    private val oidcRepository: OIDCRepository,
    private val userRepository: UserRepository
) {
    data class Input(
        val userId: UserId,
        val provider: OIDCProvider,
        val code: String,
        val state: String,
        val codeVerifier: String
    )

    data class Output(
        val account: OIDCAccount
    )

    suspend operator fun invoke(input: Input): Output {
        // 1. ユーザーの存在確認
        val user = userRepository.findById(input.userId)
            ?: throw DomainError.NotFound.user(input.userId.value)

        // 2. 既にリンク済みか確認
        val existingLink = oidcRepository.findByUserIdAndProvider(input.userId, input.provider)
        if (existingLink != null) {
            throw DomainError.OIDC.alreadyLinked(input.provider.id)
        }

        // 3. State 検証
        val storedState = oidcRepository.validateAndConsumeState(input.state)
            ?: throw DomainError.OIDC.invalidState()

        if (storedState.provider != input.provider) {
            throw DomainError.OIDC.invalidState()
        }

        // 4. トークン交換
        val tokenResponse = oidcRepository.exchangeCodeForTokens(
            provider = input.provider,
            code = input.code,
            codeVerifier = input.codeVerifier,
            redirectUri = storedState.redirectUri
        )

        // 5. ID Token 検証
        val claims = oidcRepository.validateIdToken(
            provider = input.provider,
            idToken = tokenResponse.idToken,
            nonce = storedState.nonce
        )

        // 6. 他のユーザーに既にリンクされていないか確認
        val existingAccount = oidcRepository.findByProviderAndProviderUserId(
            provider = input.provider,
            providerUserId = claims.subject
        )
        if (existingAccount != null && existingAccount.userId != input.userId) {
            throw DomainError.OIDC.alreadyLinked(input.provider.id)
        }

        // 7. OIDC アカウント作成
        val oidcAccount = OIDCAccount(
            id = OIDCAccountId(UUID.randomUUID().toString()),
            userId = input.userId,
            provider = input.provider,
            providerUserId = claims.subject,
            email = claims.email,
            name = claims.name,
            avatarUrl = claims.picture
        )

        val savedAccount = oidcRepository.save(oidcAccount)

        return Output(account = savedAccount)
    }
}
```

## UnlinkOIDCProviderUseCase.kt

```kotlin
package {{package}}.domain.usecase.oidc

import {{package}}.domain.error.DomainError
import {{package}}.domain.model.OIDCProvider
import {{package}}.domain.model.UserId
import {{package}}.domain.repository.OIDCRepository
import {{package}}.domain.repository.UserRepository

/**
 * OIDC プロバイダーのリンク解除
 *
 * 注意: 最後の認証方法を削除することはできない
 */
class UnlinkOIDCProviderUseCase(
    private val oidcRepository: OIDCRepository,
    private val userRepository: UserRepository
) {
    data class Input(
        val userId: UserId,
        val provider: OIDCProvider
    )

    suspend operator fun invoke(input: Input) {
        // 1. ユーザーの存在確認
        val user = userRepository.findById(input.userId)
            ?: throw DomainError.NotFound.user(input.userId.value)

        // 2. リンク済みか確認
        val existingAccount = oidcRepository.findByUserIdAndProvider(input.userId, input.provider)
            ?: throw DomainError.OIDC.notLinked(input.provider.id)

        // 3. 他の認証方法が残るか確認
        val linkedAccounts = oidcRepository.findByUserId(input.userId)
        val hasPasswordAuth = user.passwordHash != null
        val otherOIDCCount = linkedAccounts.count { it.provider != input.provider }

        if (!hasPasswordAuth && otherOIDCCount == 0) {
            // パスワードもなく、他の OIDC リンクもない場合は削除不可
            throw DomainError.OIDC.cannotUnlink()
        }

        // 4. リンク解除
        oidcRepository.delete(existingAccount.id)
    }
}
```

## GetLinkedOIDCAccountsUseCase.kt

```kotlin
package {{package}}.domain.usecase.oidc

import {{package}}.domain.model.OIDCAccount
import {{package}}.domain.model.UserId
import {{package}}.domain.repository.OIDCRepository

/**
 * リンク済み OIDC アカウント一覧取得
 */
class GetLinkedOIDCAccountsUseCase(
    private val oidcRepository: OIDCRepository
) {
    suspend operator fun invoke(userId: UserId): List<OIDCAccount> {
        return oidcRepository.findByUserId(userId)
    }
}
```
