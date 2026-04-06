# OIDC Domain 層リファレンス

OIDC 認証に関する Domain 層のコードテンプレートです。

## OIDCProvider.kt

```kotlin
package {{package}}.domain.model

/**
 * OIDC プロバイダー
 */
enum class OIDCProvider(val id: String, val displayName: String) {
    GOOGLE("google", "Google"),
    APPLE("apple", "Apple"),
    GITHUB("github", "GitHub"),
    LINE("line", "LINE");

    companion object {
        fun fromId(id: String): OIDCProvider? =
            entries.find { it.id == id }
    }
}
```

## OIDCAccount.kt

```kotlin
package {{package}}.domain.model

import java.time.Instant

/**
 * OIDC プロバイダーとリンクされたアカウント
 *
 * @property id アカウントID
 * @property userId リンク先のユーザーID
 * @property provider OIDC プロバイダー
 * @property providerUserId プロバイダー側のユーザーID（sub）
 * @property email プロバイダーから取得したメールアドレス
 * @property name プロバイダーから取得した名前
 * @property avatarUrl プロバイダーから取得したアバター URL
 */
data class OIDCAccount(
    val id: OIDCAccountId,
    val userId: UserId,
    val provider: OIDCProvider,
    val providerUserId: String,
    val email: String?,
    val name: String?,
    val avatarUrl: String?,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now()
)

@JvmInline
value class OIDCAccountId(val value: String)
```

## OIDCState.kt

```kotlin
package {{package}}.domain.model

import java.time.Instant

/**
 * OIDC 認証状態
 *
 * CSRF 対策とPKCE のための一時的な状態を保持
 *
 * @property id 状態ID
 * @property state 一意の状態値（CSRF トークン）
 * @property provider OIDC プロバイダー
 * @property codeChallenge PKCE の code_challenge
 * @property redirectUri コールバック後のリダイレクト先
 * @property nonce ID Token 検証用の nonce（オプション）
 * @property expiresAt 有効期限
 */
data class OIDCState(
    val id: OIDCStateId,
    val state: String,
    val provider: OIDCProvider,
    val codeChallenge: String,
    val redirectUri: String,
    val nonce: String?,
    val expiresAt: Instant,
    val createdAt: Instant = Instant.now()
)

@JvmInline
value class OIDCStateId(val value: String)
```

## OIDCTokens.kt

```kotlin
package {{package}}.domain.model

/**
 * OIDC プロバイダーから取得したトークン
 */
data class OIDCTokenResponse(
    val accessToken: String,
    val idToken: String,
    val refreshToken: String?,
    val expiresIn: Int,
    val tokenType: String = "Bearer"
)

/**
 * ID Token のクレーム（検証済み）
 *
 * @property subject プロバイダー側のユーザーID（sub）
 * @property issuer トークン発行者
 * @property audience クライアントID
 * @property email ユーザーのメールアドレス
 * @property emailVerified メールアドレスが検証済みか
 * @property name ユーザーの表示名
 * @property picture プロフィール画像 URL
 * @property nonce リプレイ攻撃防止用の nonce
 * @property issuedAt トークン発行時刻
 * @property expiresAt トークン有効期限
 */
data class OIDCIdTokenClaims(
    val subject: String,
    val issuer: String,
    val audience: String,
    val email: String?,
    val emailVerified: Boolean?,
    val name: String?,
    val picture: String?,
    val nonce: String?,
    val issuedAt: Long,
    val expiresAt: Long
)

/**
 * UserInfo エンドポイントから取得した情報
 */
data class OIDCUserInfo(
    val subject: String,
    val email: String?,
    val emailVerified: Boolean?,
    val name: String?,
    val givenName: String?,
    val familyName: String?,
    val picture: String?,
    val locale: String?
)
```

## OIDCRepository.kt

```kotlin
package {{package}}.domain.repository

import {{package}}.domain.model.*

/**
 * OIDC リポジトリインターフェース
 *
 * 認証状態の管理、OIDC アカウントの永続化、
 * プロバイダーとの通信を担当
 */
interface OIDCRepository {
    // ========================================
    // State 管理 (CSRF/PKCE)
    // ========================================

    /**
     * 認証状態を作成
     *
     * @param provider OIDC プロバイダー
     * @param codeChallenge PKCE の code_challenge
     * @param redirectUri コールバック後のリダイレクト先
     * @param nonce ID Token 検証用の nonce（オプション）
     * @return 作成された認証状態
     */
    suspend fun createState(
        provider: OIDCProvider,
        codeChallenge: String,
        redirectUri: String,
        nonce: String? = null
    ): OIDCState

    /**
     * 状態を検証し、使用済みにする
     *
     * 検証後は再利用できないようにする（ワンタイム）
     *
     * @param state 状態値
     * @return 有効な場合は状態オブジェクト、無効な場合は null
     */
    suspend fun validateAndConsumeState(state: String): OIDCState?

    // ========================================
    // OIDC アカウント管理
    // ========================================

    /**
     * プロバイダーとプロバイダーユーザーIDで検索
     *
     * @param provider OIDC プロバイダー
     * @param providerUserId プロバイダー側のユーザーID
     * @return 見つかった場合は OIDC アカウント
     */
    suspend fun findByProviderAndProviderUserId(
        provider: OIDCProvider,
        providerUserId: String
    ): OIDCAccount?

    /**
     * ユーザーIDでリンク済みアカウントを検索
     *
     * @param userId ユーザーID
     * @return リンク済みの OIDC アカウントリスト
     */
    suspend fun findByUserId(userId: UserId): List<OIDCAccount>

    /**
     * ユーザーIDとプロバイダーでアカウントを検索
     *
     * @param userId ユーザーID
     * @param provider OIDC プロバイダー
     * @return 見つかった場合は OIDC アカウント
     */
    suspend fun findByUserIdAndProvider(
        userId: UserId,
        provider: OIDCProvider
    ): OIDCAccount?

    /**
     * OIDC アカウントを保存
     *
     * @param account 保存する OIDC アカウント
     * @return 保存された OIDC アカウント
     */
    suspend fun save(account: OIDCAccount): OIDCAccount

    /**
     * OIDC アカウントを削除
     *
     * @param id 削除する OIDC アカウントのID
     */
    suspend fun delete(id: OIDCAccountId)

    // ========================================
    // トークン交換・検証
    // ========================================

    /**
     * 認可コードをトークンに交換
     *
     * @param provider OIDC プロバイダー
     * @param code 認可コード
     * @param codeVerifier PKCE の code_verifier
     * @param redirectUri コールバック URI
     * @return トークンレスポンス
     */
    suspend fun exchangeCodeForTokens(
        provider: OIDCProvider,
        code: String,
        codeVerifier: String,
        redirectUri: String
    ): OIDCTokenResponse

    /**
     * ID Token を検証
     *
     * - 署名検証（JWKS）
     * - 発行者検証
     * - 対象者検証
     * - 有効期限検証
     * - Nonce 検証（指定時）
     *
     * @param provider OIDC プロバイダー
     * @param idToken 検証する ID Token
     * @param nonce 期待される nonce（オプション）
     * @return 検証済みのクレーム
     * @throws DomainError.Unauthorized.InvalidToken 検証失敗時
     */
    suspend fun validateIdToken(
        provider: OIDCProvider,
        idToken: String,
        nonce: String? = null
    ): OIDCIdTokenClaims

    /**
     * UserInfo エンドポイントからユーザー情報を取得
     *
     * ID Token に含まれない追加情報が必要な場合に使用
     *
     * @param provider OIDC プロバイダー
     * @param accessToken アクセストークン
     * @return ユーザー情報
     */
    suspend fun getUserInfo(
        provider: OIDCProvider,
        accessToken: String
    ): OIDCUserInfo

    // ========================================
    // 認証 URL 生成
    // ========================================

    /**
     * 認証 URL を生成
     *
     * @param provider OIDC プロバイダー
     * @param state 状態値
     * @param codeChallenge PKCE の code_challenge
     * @param redirectUri コールバック URI
     * @param nonce nonce（オプション）
     * @param scopes 要求するスコープ
     * @return 認証 URL
     */
    fun buildAuthorizationUrl(
        provider: OIDCProvider,
        state: String,
        codeChallenge: String,
        redirectUri: String,
        nonce: String? = null,
        scopes: List<String> = listOf("openid", "email", "profile")
    ): String
}
```

## DomainError への追加

既存の `DomainError.kt` に以下を追加:

```kotlin
sealed class DomainError : Exception() {
    // ... 既存のエラー ...

    sealed class OIDC : DomainError() {
        data class InvalidState(
            override val message: String = "Invalid or expired state"
        ) : OIDC()

        data class InvalidIdToken(
            override val message: String = "Invalid ID token"
        ) : OIDC()

        data class ProviderNotEnabled(
            val provider: String,
            override val message: String = "Provider '$provider' is not enabled"
        ) : OIDC()

        data class AlreadyLinked(
            val provider: String,
            override val message: String = "Account already linked to '$provider'"
        ) : OIDC()

        data class NotLinked(
            val provider: String,
            override val message: String = "Account not linked to '$provider'"
        ) : OIDC()

        data class CannotUnlink(
            override val message: String = "Cannot unlink the only authentication method"
        ) : OIDC()

        data class ProviderError(
            val provider: String,
            val reason: String,
            override val message: String = "OIDC provider error: $reason"
        ) : OIDC()

        companion object {
            fun invalidState() = InvalidState()
            fun invalidIdToken() = InvalidIdToken()
            fun providerNotEnabled(provider: String) = ProviderNotEnabled(provider)
            fun alreadyLinked(provider: String) = AlreadyLinked(provider)
            fun notLinked(provider: String) = NotLinked(provider)
            fun cannotUnlink() = CannotUnlink()
            fun providerError(provider: String, reason: String) = ProviderError(provider, reason)
        }
    }
}
```

## User モデルへの変更

`User.kt` の `passwordHash` を nullable に変更（OIDC のみのユーザー対応）:

```kotlin
data class User(
    val id: UserId,
    val email: Email?,                 // nullable に変更（Apple Private Relay 対応）
    val username: Username,
    val displayName: String,
    val bio: String? = null,
    val avatarUrl: String? = null,
    val passwordHash: String?,         // nullable に変更（OIDC ユーザー対応）
    val emailVerified: Boolean = false,
    val followerCount: Int = 0,
    val followingCount: Int = 0,
    val isVerified: Boolean = false,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now()
) {
    /**
     * パスワード認証が有効かどうか
     */
    val hasPasswordAuth: Boolean
        get() = passwordHash != null

    /**
     * 認証方法が存在するかどうか
     * パスワードまたはOIDCリンクのいずれかが必要
     */
    fun hasAnyAuthMethod(oidcAccountCount: Int): Boolean =
        hasPasswordAuth || oidcAccountCount > 0
}
```
