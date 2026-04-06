# OIDC プロバイダー別設定ガイド

各 OIDC プロバイダーの設定手順と注意点です。

## Google

### 設定手順

1. **Google Cloud Console でプロジェクト作成**
   - [Google Cloud Console](https://console.cloud.google.com/) にアクセス
   - 新しいプロジェクトを作成（または既存のプロジェクトを選択）

2. **OAuth 同意画面を設定**
   - APIs & Services > OAuth consent screen
   - User Type: External（または Internal）
   - アプリ名、サポートメール、デベロッパー連絡先を設定
   - スコープ: `email`, `profile`, `openid` を追加

3. **OAuth 2.0 クライアント ID を作成**

   **Web アプリケーション用:**
   - APIs & Services > Credentials > Create Credentials > OAuth client ID
   - Application type: Web application
   - Authorized redirect URIs: `https://your-domain.com/api/v1/auth/oidc/google/callback`

   **iOS アプリ用:**
   - Application type: iOS
   - Bundle ID: `com.example.yourapp`

   **Android アプリ用:**
   - Application type: Android
   - Package name: `com.example.yourapp`
   - SHA-1 certificate fingerprint: 開発用と本番用の両方を登録

4. **環境変数を設定**
   ```bash
   OIDC_GOOGLE_ENABLED=true
   GOOGLE_CLIENT_ID=xxx.apps.googleusercontent.com
   GOOGLE_CLIENT_SECRET=GOCSPX-xxx
   GOOGLE_IOS_CLIENT_ID=xxx.apps.googleusercontent.com
   GOOGLE_ANDROID_CLIENT_ID=xxx.apps.googleusercontent.com
   ```

### エンドポイント

| 用途 | URL |
|-----|-----|
| Authorization | `https://accounts.google.com/o/oauth2/v2/auth` |
| Token | `https://oauth2.googleapis.com/token` |
| JWKS | `https://www.googleapis.com/oauth2/v3/certs` |
| UserInfo | `https://openidconnect.googleapis.com/v1/userinfo` |
| Issuer | `https://accounts.google.com` |

### ID Token クレーム

```json
{
  "iss": "https://accounts.google.com",
  "azp": "xxx.apps.googleusercontent.com",
  "aud": "xxx.apps.googleusercontent.com",
  "sub": "1234567890",
  "email": "user@example.com",
  "email_verified": true,
  "name": "John Doe",
  "picture": "https://lh3.googleusercontent.com/...",
  "given_name": "John",
  "family_name": "Doe",
  "locale": "en",
  "iat": 1234567890,
  "exp": 1234567890
}
```

### 注意点

- iOS/Android の Client ID は Web とは別に発行される
- ネイティブ SDK を使用する場合、`audience` に対応する Client ID を検証に含める
- `refresh_token` は初回認証時のみ返却される（`access_type=offline` + `prompt=consent` が必要）

---

## Apple

### 設定手順

1. **Apple Developer でアプリ ID を作成**
   - [Apple Developer](https://developer.apple.com/) > Certificates, Identifiers & Profiles
   - Identifiers > App IDs > 新規作成
   - Sign in with Apple を有効化

2. **Service ID を作成（Web 認証用）**
   - Identifiers > Services IDs > 新規作成
   - Identifier: `com.example.app.signin`（Bundle ID とは別）
   - Sign in with Apple を有効化
   - Configure:
     - Primary App ID: 上で作成した App ID
     - Domains: `your-domain.com`
     - Return URLs: `https://your-domain.com/api/v1/auth/oidc/apple/callback`

3. **Private Key を作成**
   - Keys > 新規作成
   - Key Name: 任意の名前
   - Sign in with Apple を有効化、Configure で App ID を選択
   - ダウンロードした .p8 ファイルを安全に保管（再ダウンロード不可）
   - Key ID をメモ

4. **環境変数を設定**
   ```bash
   OIDC_APPLE_ENABLED=true
   APPLE_CLIENT_ID=com.example.app.signin
   APPLE_TEAM_ID=XXXXXXXXXX
   APPLE_KEY_ID=XXXXXXXXXX
   # Private Key は改行を \n でエスケープ
   APPLE_PRIVATE_KEY="-----BEGIN PRIVATE KEY-----\nMIGTAgEAMBMGByqGSM49AgEGCCqGSM49AwEHBHkwdwIBAQQg...\n-----END PRIVATE KEY-----"
   ```

### エンドポイント

| 用途 | URL |
|-----|-----|
| Authorization | `https://appleid.apple.com/auth/authorize` |
| Token | `https://appleid.apple.com/auth/token` |
| JWKS | `https://appleid.apple.com/auth/keys` |
| Issuer | `https://appleid.apple.com` |

### Client Secret の生成

Apple は Client Secret として JWT を要求します:

```kotlin
fun generateClientSecret(): String {
    val now = System.currentTimeMillis()
    val expiresAt = Date(now + 15777000L * 1000) // 約6ヶ月

    val privateKey = loadPrivateKey(config.privateKey)

    return JWT.create()
        .withIssuer(config.teamId)           // Team ID
        .withIssuedAt(Date(now))
        .withExpiresAt(expiresAt)            // 最大6ヶ月
        .withAudience("https://appleid.apple.com")
        .withSubject(config.clientId)        // Service ID
        .withKeyId(config.keyId)             // Key ID
        .sign(Algorithm.ECDSA256(null, privateKey))
}
```

### ID Token クレーム

```json
{
  "iss": "https://appleid.apple.com",
  "aud": "com.example.app.signin",
  "exp": 1234567890,
  "iat": 1234567890,
  "sub": "001234.abcd1234abcd1234abcd1234abcd1234.5678",
  "nonce": "random-nonce-value",
  "c_hash": "xxx",
  "email": "user@example.com",
  "email_verified": "true",
  "is_private_email": "false",
  "auth_time": 1234567890,
  "nonce_supported": true
}
```

### 注意点

- **Private Email Relay**: ユーザーが「メールを非公開」を選択すると `xxx@privaterelay.appleid.com` 形式のメールが返される
- **名前は初回のみ**: ユーザー名は初回ログイン時のみ取得可能（Authorization Response に含まれる）
- **UserInfo なし**: Apple は UserInfo エンドポイントを提供しない
- **response_mode**: Web では `form_post` を使用

### 初回ログイン時の名前取得

```kotlin
// Authorization Response に含まれる user パラメータ
@Serializable
data class AppleAuthorizationUser(
    val name: AppleUserName?,
    val email: String?
)

@Serializable
data class AppleUserName(
    val firstName: String?,
    val lastName: String?
)

// クライアントから送信された user 情報を処理
fun handleAppleCallback(
    code: String,
    state: String,
    user: String? // JSON 文字列（初回のみ）
) {
    val userInfo = user?.let {
        Json.decodeFromString<AppleAuthorizationUser>(it)
    }
    val name = userInfo?.name?.let { "${it.firstName} ${it.lastName}" }
}
```

---

## GitHub (OAuth 2.0)

GitHub は OIDC ではなく OAuth 2.0 のみサポート。ID Token は発行されないため、UserInfo API でユーザー情報を取得します。

### 設定手順

1. **GitHub Developer Settings で OAuth App を作成**
   - Settings > Developer settings > OAuth Apps > New OAuth App
   - Application name: 任意
   - Homepage URL: `https://your-domain.com`
   - Authorization callback URL: `https://your-domain.com/api/v1/auth/oidc/github/callback`

2. **環境変数を設定**
   ```bash
   OIDC_GITHUB_ENABLED=true
   GITHUB_CLIENT_ID=xxx
   GITHUB_CLIENT_SECRET=xxx
   ```

### エンドポイント

| 用途 | URL |
|-----|-----|
| Authorization | `https://github.com/login/oauth/authorize` |
| Token | `https://github.com/login/oauth/access_token` |
| UserInfo | `https://api.github.com/user` |
| User Emails | `https://api.github.com/user/emails` |

### 実装の違い

```kotlin
class GitHubOAuthClient(
    private val httpClient: HttpClient,
    private val config: GitHubOAuthConfig
) : OIDCClient {

    override val provider = OIDCProvider.GITHUB

    // GitHub は ID Token を発行しないため、validateIdToken は使用しない
    override suspend fun validateIdToken(idToken: String, nonce: String?): OIDCIdTokenClaims {
        throw UnsupportedOperationException("GitHub does not support ID Token")
    }

    // 代わりに getUserInfo で認証を行う
    override suspend fun getUserInfo(accessToken: String): OIDCUserInfo {
        val user: GitHubUser = httpClient.get("https://api.github.com/user") {
            bearerAuth(accessToken)
            header("Accept", "application/vnd.github.v3+json")
        }.body()

        // プライマリメールを取得
        val emails: List<GitHubEmail> = httpClient.get("https://api.github.com/user/emails") {
            bearerAuth(accessToken)
            header("Accept", "application/vnd.github.v3+json")
        }.body()

        val primaryEmail = emails.find { it.primary && it.verified }

        return OIDCUserInfo(
            subject = user.id.toString(),
            email = primaryEmail?.email,
            emailVerified = primaryEmail?.verified,
            name = user.name,
            givenName = null,
            familyName = null,
            picture = user.avatarUrl,
            locale = null
        )
    }
}
```

---

## LINE (OIDC)

### 設定手順

1. **LINE Developers Console でチャネルを作成**
   - [LINE Developers](https://developers.line.biz/) > Providers > 新規作成
   - LINE Login チャネルを作成

2. **チャネル設定**
   - Callback URL: `https://your-domain.com/api/v1/auth/oidc/line/callback`
   - Scope: `openid`, `profile`, `email`（email は申請が必要な場合あり）

3. **環境変数を設定**
   ```bash
   OIDC_LINE_ENABLED=true
   LINE_CHANNEL_ID=xxx
   LINE_CHANNEL_SECRET=xxx
   ```

### エンドポイント

| 用途 | URL |
|-----|-----|
| Authorization | `https://access.line.me/oauth2/v2.1/authorize` |
| Token | `https://api.line.me/oauth2/v2.1/token` |
| JWKS | `https://api.line.me/oauth2/v2.1/certs` |
| UserInfo | `https://api.line.me/v2/profile` |
| Issuer | `https://access.line.me` |

### ID Token クレーム

```json
{
  "iss": "https://access.line.me",
  "sub": "U1234567890abcdef1234567890abcdef",
  "aud": "1234567890",
  "exp": 1234567890,
  "iat": 1234567890,
  "nonce": "random-nonce-value",
  "amr": ["linesso"],
  "name": "LINE User",
  "picture": "https://profile.line-scdn.net/..."
}
```

### 注意点

- **email スコープ**: 通常は申請が必要
- **UserInfo**: ID Token に含まれる情報は限定的なため、追加情報が必要な場合は Profile API を使用
- **日本市場向け**: 日本での利用率が高い

---

## セキュリティ共通事項

### PKCE 実装

```kotlin
// code_verifier 生成（43-128文字）
fun generateCodeVerifier(): String {
    val bytes = ByteArray(32)
    SecureRandom().nextBytes(bytes)
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
}

// code_challenge 生成（S256）
fun generateCodeChallenge(codeVerifier: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val hash = digest.digest(codeVerifier.toByteArray(Charsets.US_ASCII))
    return Base64.getUrlEncoder().withoutPadding().encodeToString(hash)
}
```

### State パラメータ

```kotlin
// State 生成（32バイトのランダム値）
fun generateState(): String {
    val bytes = ByteArray(32)
    SecureRandom().nextBytes(bytes)
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
}
```

### Nonce 検証

```kotlin
// Nonce 生成
fun generateNonce(): String {
    val bytes = ByteArray(16)
    SecureRandom().nextBytes(bytes)
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
}

// ID Token の nonce と比較
fun validateNonce(idTokenClaims: OIDCIdTokenClaims, expectedNonce: String): Boolean {
    return idTokenClaims.nonce == expectedNonce
}
```

### クライアント ID 検証

ネイティブ SDK からのトークンを検証する際、正しいクライアント ID で発行されたか確認:

```kotlin
fun validateAudience(claims: OIDCIdTokenClaims, config: OIDCConfig): Boolean {
    val validAudiences = listOfNotNull(
        config.clientId,
        config.iosClientId,
        config.androidClientId
    )
    return claims.audience in validAudiences
}
```
