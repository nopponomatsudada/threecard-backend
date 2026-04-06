# セキュリティガイド

このドキュメントは、バックエンド開発におけるセキュリティのベストプラクティスと実装ガイドを提供します。

## 目次

1. [OWASP Top 10 対応](#owasp-top-10-対応)
2. [認証・認可](#認証認可)
3. [暗号化](#暗号化)
4. [HTTP セキュリティ](#http-セキュリティ)
5. [入力バリデーション](#入力バリデーション)
6. [レート制限](#レート制限)
7. [ロギング・監査](#ロギング監査)
8. [シークレット管理](#シークレット管理)
9. [データベースセキュリティ](#データベースセキュリティ)

---

## OWASP Top 10 対応

### A01:2021 - Broken Access Control（アクセス制御の不備）

**対策:**

```kotlin
// routes/UserRoutes.kt
fun Route.userRoutes(getUserUseCase: GetUserUseCase) {
    authenticate("jwt") {
        get("/users/{id}") {
            val requestedUserId = call.parameters["id"] ?: throw BadRequestException("User ID required")
            val currentUser = call.principal<JWTPrincipal>()
            val currentUserId = currentUser?.payload?.getClaim("userId")?.asString()

            // リソース所有者チェック（または管理者権限チェック）
            if (currentUserId != requestedUserId && !hasAdminRole(currentUser)) {
                throw ForbiddenException("Access denied to this resource")
            }

            val user = getUserUseCase(UserId(requestedUserId))
            call.respond(user.toResponse())
        }
    }
}

// 権限チェックヘルパー
private fun hasAdminRole(principal: JWTPrincipal?): Boolean {
    val roles = principal?.payload?.getClaim("roles")?.asList(String::class.java) ?: emptyList()
    return "ADMIN" in roles
}
```

**チェックリスト:**
- [ ] すべてのエンドポイントで認証を要求
- [ ] リソースへのアクセス時に所有者チェックを実施
- [ ] ロールベースのアクセス制御（RBAC）を実装
- [ ] 水平権限昇格（他ユーザーのリソースへのアクセス）を防止
- [ ] 垂直権限昇格（管理者機能へのアクセス）を防止

### A02:2021 - Cryptographic Failures（暗号化の失敗）

**対策:**

```kotlin
// domain/service/PasswordHasher.kt
interface PasswordHasher {
    fun hash(password: String): String
    fun verify(password: String, hash: String): Boolean
}

// data/service/BCryptPasswordHasher.kt
class BCryptPasswordHasher : PasswordHasher {
    private val bcrypt = BCrypt.withDefaults()
    private val verifier = BCrypt.verifyer()

    override fun hash(password: String): String {
        // コスト係数12（推奨: 10-14）
        return bcrypt.hashToString(12, password.toCharArray())
    }

    override fun verify(password: String, hash: String): Boolean {
        return verifier.verify(password.toCharArray(), hash).verified
    }
}
```

**チェックリスト:**
- [ ] パスワードは BCrypt（コスト係数 12 以上）でハッシュ化
- [ ] JWT 署名は RS256（本番）または HS256（開発）を使用
- [ ] 機密データは TLS 1.2 以上で送信
- [ ] 暗号化キーは環境変数または KMS で管理
- [ ] 弱いアルゴリズム（MD5, SHA1）を使用しない

### A03:2021 - Injection（インジェクション）

**対策:**

```kotlin
// Exposed のパラメータバインディングを使用（SQLインジェクション対策）
// ❌ 悪い例
fun findByEmailBad(email: String): User? {
    return transaction {
        exec("SELECT * FROM users WHERE email = '$email'") // 危険！
    }
}

// ✅ 良い例
fun findByEmail(email: String): User? {
    return transaction {
        UserTable.select { UserTable.email eq email }
            .map { it.toUser() }
            .singleOrNull()
    }
}
```

**チェックリスト:**
- [ ] ORM（Exposed）のパラメータバインディングを使用
- [ ] 生の SQL クエリを避ける
- [ ] 外部コマンド実行時は引数をエスケープ
- [ ] LDAP、XPath クエリでもパラメータ化

### A04:2021 - Insecure Design（安全でない設計）

**対策:**
- Clean Architecture による関心の分離
- セキュリティ要件を設計段階で考慮
- 脅威モデリングの実施

**チェックリスト:**
- [ ] 設計段階でセキュリティ要件を定義
- [ ] 認証フローの設計レビュー
- [ ] データフローの機密性確認

### A05:2021 - Security Misconfiguration（セキュリティ設定ミス）

**対策:**

```kotlin
// plugins/Security.kt
fun Application.configureSecurity() {
    // セキュリティヘッダー
    install(DefaultHeaders) {
        header("X-Frame-Options", "DENY")
        header("X-Content-Type-Options", "nosniff")
        header("X-XSS-Protection", "1; mode=block")
        header("Strict-Transport-Security", "max-age=31536000; includeSubDomains")
        header("Content-Security-Policy", "default-src 'self'")
        header("Referrer-Policy", "strict-origin-when-cross-origin")
        header("Permissions-Policy", "geolocation=(), microphone=(), camera=()")
    }

    // CORS設定
    install(CORS) {
        // 許可するオリジン（環境変数から読み込む）
        val allowedOrigins = System.getenv("ALLOWED_ORIGINS")?.split(",") ?: listOf()
        allowedOrigins.forEach { allowHost(it) }

        allowCredentials = true
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        maxAgeInSeconds = 3600
    }
}
```

**チェックリスト:**
- [ ] 不要な機能・エンドポイントを無効化
- [ ] デフォルト認証情報を変更
- [ ] エラーメッセージで詳細情報を漏らさない
- [ ] セキュリティヘッダーを設定
- [ ] 本番環境でデバッグモードを無効化

### A06:2021 - Vulnerable and Outdated Components（脆弱なコンポーネント）

**対策:**

```kotlin
// build.gradle.kts
plugins {
    id("org.owasp.dependencycheck") version "8.4.0"
}

dependencyCheck {
    failBuildOnCVSS = 7.0f // CVSS 7.0 以上で失敗
    suppressionFile = "config/dependency-check-suppressions.xml"
}
```

**チェックリスト:**
- [ ] 依存関係を定期的に更新
- [ ] OWASP Dependency Check を CI に組み込む
- [ ] 既知の脆弱性をモニタリング
- [ ] 使用していない依存関係を削除

### A07:2021 - Identification and Authentication Failures（認証の失敗）

→ [認証・認可](#認証認可) セクションを参照

### A08:2021 - Software and Data Integrity Failures（ソフトウェアとデータの整合性の失敗）

**対策:**
- CI/CD パイプラインでの署名検証
- 依存関係のハッシュ検証
- コード署名

**チェックリスト:**
- [ ] Gradle の依存関係ロックを使用
- [ ] CI/CD パイプラインのアクセス制御
- [ ] デプロイ成果物の署名

### A09:2021 - Security Logging and Monitoring Failures（ロギングとモニタリングの失敗）

→ [ロギング・監査](#ロギング監査) セクションを参照

### A10:2021 - Server-Side Request Forgery (SSRF)

**対策:**

```kotlin
// 外部URLへのリクエスト時の検証
fun validateExternalUrl(url: String): Boolean {
    val uri = URI(url)

    // 内部ネットワークへのアクセスを禁止
    val blockedHosts = listOf(
        "localhost",
        "127.0.0.1",
        "0.0.0.0",
        "169.254.169.254", // AWS メタデータ
        "metadata.google.internal" // GCP メタデータ
    )

    if (uri.host in blockedHosts || uri.host.startsWith("192.168.") || uri.host.startsWith("10.")) {
        return false
    }

    // 許可するスキームを制限
    if (uri.scheme !in listOf("http", "https")) {
        return false
    }

    return true
}
```

**チェックリスト:**
- [ ] 外部 URL へのリクエスト時にホワイトリスト検証
- [ ] 内部ネットワークアドレスへのアクセスをブロック
- [ ] クラウドメタデータ URL へのアクセスをブロック

---

## 認証・認可

### JWT 実装ガイド

#### 推奨設定

| 設定項目 | 開発環境 | 本番環境 |
|---------|---------|---------|
| 署名アルゴリズム | HS256 | RS256 |
| アクセストークン有効期限 | 1時間 | 15分 |
| リフレッシュトークン有効期限 | 30日 | 7日 |
| 秘密鍵管理 | 環境変数 | KMS / Vault |

#### 実装例

```kotlin
// plugins/Authentication.kt
fun Application.configureAuthentication() {
    val jwtSecret = System.getenv("JWT_SECRET")
        ?: throw IllegalStateException("JWT_SECRET not configured")
    val jwtIssuer = System.getenv("JWT_ISSUER") ?: "appmaster"
    val jwtAudience = System.getenv("JWT_AUDIENCE") ?: "appmaster-api"

    install(Authentication) {
        jwt("jwt") {
            realm = "AppMaster API"
            verifier(
                JWT.require(Algorithm.HMAC256(jwtSecret))
                    .withIssuer(jwtIssuer)
                    .withAudience(jwtAudience)
                    .build()
            )
            validate { credential ->
                // トークン検証
                if (credential.payload.audience.contains(jwtAudience)) {
                    JWTPrincipal(credential.payload)
                } else {
                    null
                }
            }
            challenge { _, _ ->
                call.respond(
                    HttpStatusCode.Unauthorized,
                    ErrorResponse("UNAUTHORIZED", "Invalid or expired token")
                )
            }
        }
    }
}

// domain/service/TokenService.kt
interface TokenService {
    fun generateAccessToken(userId: UserId, roles: List<String>): String
    fun generateRefreshToken(userId: UserId): String
    fun validateRefreshToken(token: String): UserId?
    fun revokeRefreshToken(token: String)
}

// data/service/JwtTokenService.kt
class JwtTokenService(
    private val secret: String,
    private val issuer: String,
    private val audience: String,
    private val refreshTokenRepository: RefreshTokenRepository
) : TokenService {

    companion object {
        private const val ACCESS_TOKEN_EXPIRY_MINUTES = 15L
        private const val REFRESH_TOKEN_EXPIRY_DAYS = 7L
    }

    override fun generateAccessToken(userId: UserId, roles: List<String>): String {
        return JWT.create()
            .withIssuer(issuer)
            .withAudience(audience)
            .withClaim("userId", userId.value)
            .withClaim("roles", roles)
            .withExpiresAt(Date(System.currentTimeMillis() + ACCESS_TOKEN_EXPIRY_MINUTES * 60 * 1000))
            .withIssuedAt(Date())
            .withJWTId(UUID.randomUUID().toString())
            .sign(Algorithm.HMAC256(secret))
    }

    override fun generateRefreshToken(userId: UserId): String {
        val token = UUID.randomUUID().toString()
        val expiresAt = Instant.now().plus(REFRESH_TOKEN_EXPIRY_DAYS, ChronoUnit.DAYS)

        refreshTokenRepository.save(
            RefreshToken(
                token = token,
                userId = userId,
                expiresAt = expiresAt
            )
        )

        return token
    }

    override fun validateRefreshToken(token: String): UserId? {
        val refreshToken = refreshTokenRepository.findByToken(token) ?: return null

        if (refreshToken.expiresAt.isBefore(Instant.now())) {
            refreshTokenRepository.delete(token)
            return null
        }

        return refreshToken.userId
    }

    override fun revokeRefreshToken(token: String) {
        refreshTokenRepository.delete(token)
    }
}
```

### ロールベースアクセス制御（RBAC）

```kotlin
// domain/model/Role.kt
enum class Role {
    USER,
    MODERATOR,
    ADMIN
}

// routes/middleware/RoleAuthorization.kt
class RoleAuthorizationPlugin(
    private val requiredRoles: Set<Role>
) {
    companion object : BaseRouteScopedPlugin<RoleAuthorizationConfig, RoleAuthorizationPlugin> {
        override val key = AttributeKey<RoleAuthorizationPlugin>("RoleAuthorization")

        override fun install(
            pipeline: ApplicationCallPipeline,
            configure: RoleAuthorizationConfig.() -> Unit
        ): RoleAuthorizationPlugin {
            val config = RoleAuthorizationConfig().apply(configure)
            val plugin = RoleAuthorizationPlugin(config.roles)

            pipeline.intercept(ApplicationCallPipeline.Plugins) {
                val principal = call.principal<JWTPrincipal>()
                val userRoles = principal?.payload
                    ?.getClaim("roles")
                    ?.asList(String::class.java)
                    ?.mapNotNull { runCatching { Role.valueOf(it) }.getOrNull() }
                    ?.toSet()
                    ?: emptySet()

                if (config.roles.none { it in userRoles }) {
                    call.respond(
                        HttpStatusCode.Forbidden,
                        ErrorResponse("FORBIDDEN", "Insufficient permissions")
                    )
                    finish()
                }
            }

            return plugin
        }
    }
}

class RoleAuthorizationConfig {
    var roles: Set<Role> = emptySet()
}

// 使用例
fun Route.adminRoutes() {
    authenticate("jwt") {
        install(RoleAuthorizationPlugin) {
            roles = setOf(Role.ADMIN)
        }

        get("/admin/users") {
            // 管理者のみアクセス可能
        }
    }
}
```

### OIDC 認証セキュリティ

外部プロバイダー（Google、Apple など）を使用した認証のセキュリティ対策。

#### 必須の対策

| 対策 | 説明 | 実装 |
|-----|------|------|
| **State パラメータ** | CSRF 攻撃の防止 | 32 バイトのランダム値、1 回使用、10 分で期限切れ |
| **PKCE** | 認可コード傍受攻撃の防止 | S256 方式の code_challenge |
| **ID Token 署名検証** | トークン改ざんの防止 | JWKS による RSA 公開鍵検証 |
| **Nonce 検証** | リプレイ攻撃の防止 | ID Token 内の nonce と照合 |
| **Audience 検証** | 不正なトークンの防止 | 正しいクライアント ID かを確認 |

#### PKCE 実装

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

#### ID Token 検証

```kotlin
fun validateIdToken(idToken: String, expectedNonce: String?): OIDCIdTokenClaims {
    val decodedJwt = JWT.decode(idToken)

    // 1. JWKS から公開鍵を取得
    val jwk = jwkProvider.get(decodedJwt.keyId)
    val publicKey = jwk.publicKey as RSAPublicKey

    // 2. 署名検証
    val verifier = JWT.require(Algorithm.RSA256(publicKey, null))
        .withIssuer(EXPECTED_ISSUER)
        .withAudience(CLIENT_ID)
        .build()
    val verified = verifier.verify(idToken)

    // 3. Nonce 検証（リプレイ攻撃防止）
    if (expectedNonce != null) {
        val tokenNonce = verified.getClaim("nonce").asString()
        if (tokenNonce != expectedNonce) {
            throw SecurityException("Nonce mismatch")
        }
    }

    return extractClaims(verified)
}
```

#### ネイティブ SDK 使用時の注意

モバイルアプリで Google Sign-In や Sign in with Apple SDK を使用する場合:

```kotlin
// 複数のクライアント ID を許可（Web + iOS + Android）
fun validateAudience(claims: OIDCIdTokenClaims): Boolean {
    val validAudiences = listOfNotNull(
        config.webClientId,
        config.iosClientId,
        config.androidClientId
    )
    return claims.audience in validAudiences
}
```

#### OIDC セキュリティチェックリスト

- [ ] State パラメータをランダム生成し、サーバー側で検証
- [ ] PKCE (S256) を使用
- [ ] ID Token を JWKS で署名検証
- [ ] Issuer と Audience を検証
- [ ] Nonce を使用してリプレイ攻撃を防止
- [ ] State の有効期限を短く設定（10 分以下）
- [ ] 使用済み State を再利用不可にする
- [ ] プロバイダーエラーの詳細を外部に漏らさない

---

## 暗号化

### パスワードハッシュ化

```kotlin
// build.gradle.kts
dependencies {
    implementation("at.favre.lib:bcrypt:0.10.2")
}

// data/service/BCryptPasswordHasher.kt
class BCryptPasswordHasher : PasswordHasher {
    // コスト係数の推奨値
    // - 開発環境: 10（高速）
    // - 本番環境: 12-14（セキュア）
    private val costFactor = System.getenv("BCRYPT_COST")?.toIntOrNull() ?: 12

    private val bcrypt = BCrypt.withDefaults()
    private val verifier = BCrypt.verifyer()

    override fun hash(password: String): String {
        return bcrypt.hashToString(costFactor, password.toCharArray())
    }

    override fun verify(password: String, hash: String): Boolean {
        return verifier.verify(password.toCharArray(), hash).verified
    }
}
```

### データ暗号化

```kotlin
// domain/service/Encryptor.kt
interface Encryptor {
    fun encrypt(plainText: String): String
    fun decrypt(cipherText: String): String
}

// data/service/AesEncryptor.kt
class AesEncryptor(
    private val secretKey: String // 32文字（AES-256）
) : Encryptor {

    private val algorithm = "AES/GCM/NoPadding"
    private val keySpec = SecretKeySpec(secretKey.toByteArray(), "AES")

    override fun encrypt(plainText: String): String {
        val cipher = Cipher.getInstance(algorithm)
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, GCMParameterSpec(128, iv))

        val encrypted = cipher.doFinal(plainText.toByteArray())
        val combined = iv + encrypted

        return Base64.getEncoder().encodeToString(combined)
    }

    override fun decrypt(cipherText: String): String {
        val decoded = Base64.getDecoder().decode(cipherText)
        val iv = decoded.sliceArray(0 until 12)
        val encrypted = decoded.sliceArray(12 until decoded.size)

        val cipher = Cipher.getInstance(algorithm)
        cipher.init(Cipher.DECRYPT_MODE, keySpec, GCMParameterSpec(128, iv))

        return String(cipher.doFinal(encrypted))
    }
}
```

---

## HTTP セキュリティ

### セキュリティヘッダー

```kotlin
// plugins/Security.kt
fun Application.configureSecurity() {
    install(DefaultHeaders) {
        // クリックジャッキング対策
        header("X-Frame-Options", "DENY")

        // MIME スニッフィング対策
        header("X-Content-Type-Options", "nosniff")

        // XSS 対策（レガシーブラウザ用）
        header("X-XSS-Protection", "1; mode=block")

        // HTTPS 強制（HSTS）
        header("Strict-Transport-Security", "max-age=31536000; includeSubDomains; preload")

        // コンテンツセキュリティポリシー
        header("Content-Security-Policy", "default-src 'self'; frame-ancestors 'none'")

        // リファラーポリシー
        header("Referrer-Policy", "strict-origin-when-cross-origin")

        // 機能ポリシー
        header("Permissions-Policy", "geolocation=(), microphone=(), camera=()")
    }
}
```

### CORS 設定

```kotlin
// plugins/Cors.kt
fun Application.configureCors() {
    val environment = System.getenv("APP_ENV") ?: "development"

    install(CORS) {
        when (environment) {
            "production" -> {
                // 本番: 明示的にオリジンを指定
                val allowedOrigins = System.getenv("ALLOWED_ORIGINS")
                    ?.split(",")
                    ?.map { it.trim() }
                    ?: throw IllegalStateException("ALLOWED_ORIGINS not configured")

                allowedOrigins.forEach { origin ->
                    allowHost(origin.removePrefix("https://").removePrefix("http://"))
                }
            }
            "development" -> {
                // 開発: localhost を許可
                allowHost("localhost:3000")
                allowHost("localhost:8080")
                allowHost("10.0.2.2:3000") // Android エミュレータ
            }
        }

        allowCredentials = true

        // 許可するヘッダー
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Accept)

        // 許可するメソッド
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Patch)
        allowMethod(HttpMethod.Delete)

        // プリフライトのキャッシュ時間
        maxAgeInSeconds = 3600
    }
}
```

---

## 入力バリデーション

### ValueObject によるバリデーション

```kotlin
// domain/model/Email.kt
@JvmInline
value class Email private constructor(val value: String) {
    companion object {
        private val EMAIL_REGEX = Regex(
            "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
        )
        private const val MAX_LENGTH = 254

        fun create(value: String): Result<Email> {
            val trimmed = value.trim().lowercase()

            return when {
                trimmed.isBlank() ->
                    Result.failure(ValidationError("Email cannot be empty"))
                trimmed.length > MAX_LENGTH ->
                    Result.failure(ValidationError("Email too long"))
                !EMAIL_REGEX.matches(trimmed) ->
                    Result.failure(ValidationError("Invalid email format"))
                else ->
                    Result.success(Email(trimmed))
            }
        }
    }
}

// domain/model/Password.kt
@JvmInline
value class Password private constructor(val value: String) {
    companion object {
        private const val MIN_LENGTH = 8
        private const val MAX_LENGTH = 128

        fun create(value: String): Result<Password> {
            return when {
                value.length < MIN_LENGTH ->
                    Result.failure(ValidationError("Password must be at least $MIN_LENGTH characters"))
                value.length > MAX_LENGTH ->
                    Result.failure(ValidationError("Password too long"))
                !value.any { it.isUpperCase() } ->
                    Result.failure(ValidationError("Password must contain uppercase letter"))
                !value.any { it.isLowerCase() } ->
                    Result.failure(ValidationError("Password must contain lowercase letter"))
                !value.any { it.isDigit() } ->
                    Result.failure(ValidationError("Password must contain digit"))
                else ->
                    Result.success(Password(value))
            }
        }
    }
}
```

### リクエストサイズ制限

```kotlin
// plugins/ContentNegotiation.kt
fun Application.configureContentNegotiation() {
    install(ContentNegotiation) {
        json(Json {
            ignoreUnknownKeys = true
            isLenient = false
        })
    }

    // リクエストボディサイズ制限（1MB）
    install(DoubleReceive)

    intercept(ApplicationCallPipeline.Plugins) {
        val contentLength = call.request.header(HttpHeaders.ContentLength)?.toLongOrNull()
        val maxSize = 1_048_576L // 1MB

        if (contentLength != null && contentLength > maxSize) {
            call.respond(
                HttpStatusCode.PayloadTooLarge,
                ErrorResponse("PAYLOAD_TOO_LARGE", "Request body too large")
            )
            finish()
        }
    }
}
```

---

## レート制限

### Ktor Rate Limiting

```kotlin
// plugins/RateLimiting.kt
fun Application.configureRateLimiting() {
    install(RateLimit) {
        // グローバルレート制限
        global {
            rateLimiter(limit = 100, refillPeriod = 1.minutes)
        }

        // 認証エンドポイント用（より厳しい制限）
        register(RateLimitName("auth")) {
            rateLimiter(limit = 5, refillPeriod = 1.minutes)
        }

        // API エンドポイント用
        register(RateLimitName("api")) {
            rateLimiter(limit = 60, refillPeriod = 1.minutes)
        }
    }
}

// routes/AuthRoutes.kt
fun Route.authRoutes(loginUseCase: LoginUseCase) {
    rateLimit(RateLimitName("auth")) {
        post("/auth/login") {
            // ログイン処理
        }

        post("/auth/register") {
            // 登録処理
        }
    }
}
```

### IP ベースのブロッキング

```kotlin
// plugins/IpFilter.kt
class IpFilterPlugin(
    private val blacklist: Set<String>,
    private val whitelist: Set<String>
) {
    companion object : BaseApplicationPlugin<Application, IpFilterConfig, IpFilterPlugin> {
        override val key = AttributeKey<IpFilterPlugin>("IpFilter")

        override fun install(
            pipeline: Application,
            configure: IpFilterConfig.() -> Unit
        ): IpFilterPlugin {
            val config = IpFilterConfig().apply(configure)
            val plugin = IpFilterPlugin(config.blacklist, config.whitelist)

            pipeline.intercept(ApplicationCallPipeline.Plugins) {
                val clientIp = call.request.origin.remoteHost

                // ホワイトリストがあればそれのみ許可
                if (config.whitelist.isNotEmpty() && clientIp !in config.whitelist) {
                    call.respond(HttpStatusCode.Forbidden, ErrorResponse("FORBIDDEN", "Access denied"))
                    finish()
                    return@intercept
                }

                // ブラックリストチェック
                if (clientIp in config.blacklist) {
                    call.respond(HttpStatusCode.Forbidden, ErrorResponse("FORBIDDEN", "Access denied"))
                    finish()
                }
            }

            return plugin
        }
    }
}

class IpFilterConfig {
    var blacklist: Set<String> = emptySet()
    var whitelist: Set<String> = emptySet()
}
```

---

## ロギング・監査

### 監査ログ

```kotlin
// domain/model/AuditLog.kt
data class AuditLog(
    val id: AuditLogId,
    val timestamp: Instant,
    val userId: UserId?,
    val action: AuditAction,
    val resource: String,
    val resourceId: String?,
    val ipAddress: String,
    val userAgent: String?,
    val details: Map<String, Any>?,
    val result: AuditResult
)

enum class AuditAction {
    // 認証
    LOGIN_SUCCESS,
    LOGIN_FAILURE,
    LOGOUT,
    TOKEN_REFRESH,
    PASSWORD_CHANGE,
    PASSWORD_RESET_REQUEST,

    // ユーザー操作
    USER_CREATE,
    USER_UPDATE,
    USER_DELETE,

    // データアクセス
    DATA_READ,
    DATA_CREATE,
    DATA_UPDATE,
    DATA_DELETE,

    // 管理者操作
    ADMIN_ACTION,
    PERMISSION_CHANGE,

    // セキュリティイベント
    SUSPICIOUS_ACTIVITY,
    RATE_LIMIT_EXCEEDED,
    INVALID_TOKEN
}

enum class AuditResult {
    SUCCESS,
    FAILURE,
    DENIED
}

// domain/repository/AuditLogRepository.kt
interface AuditLogRepository {
    suspend fun save(auditLog: AuditLog)
    suspend fun findByUserId(userId: UserId, limit: Int = 100): List<AuditLog>
    suspend fun findByAction(action: AuditAction, since: Instant): List<AuditLog>
    suspend fun findSuspiciousActivity(since: Instant): List<AuditLog>
}

// domain/service/AuditService.kt
interface AuditService {
    suspend fun log(
        action: AuditAction,
        resource: String,
        resourceId: String? = null,
        userId: UserId? = null,
        ipAddress: String,
        userAgent: String? = null,
        details: Map<String, Any>? = null,
        result: AuditResult = AuditResult.SUCCESS
    )
}

// data/service/AuditServiceImpl.kt
class AuditServiceImpl(
    private val repository: AuditLogRepository,
    private val alertService: AlertService? = null
) : AuditService {

    private val securityEvents = setOf(
        AuditAction.LOGIN_FAILURE,
        AuditAction.SUSPICIOUS_ACTIVITY,
        AuditAction.RATE_LIMIT_EXCEEDED,
        AuditAction.INVALID_TOKEN
    )

    override suspend fun log(
        action: AuditAction,
        resource: String,
        resourceId: String?,
        userId: UserId?,
        ipAddress: String,
        userAgent: String?,
        details: Map<String, Any>?,
        result: AuditResult
    ) {
        val auditLog = AuditLog(
            id = AuditLogId(UUID.randomUUID().toString()),
            timestamp = Instant.now(),
            userId = userId,
            action = action,
            resource = resource,
            resourceId = resourceId,
            ipAddress = ipAddress,
            userAgent = userAgent,
            details = details,
            result = result
        )

        repository.save(auditLog)

        // セキュリティイベントはアラート
        if (action in securityEvents) {
            alertService?.sendSecurityAlert(auditLog)
        }
    }
}
```

### 構造化ログ

```kotlin
// infrastructure/logging/StructuredLogger.kt
object StructuredLogger {
    private val logger = LoggerFactory.getLogger("app")
    private val json = Json { encodeDefaults = true }

    fun info(message: String, vararg pairs: Pair<String, Any?>) {
        logger.info(formatLog(message, pairs.toMap()))
    }

    fun warn(message: String, vararg pairs: Pair<String, Any?>) {
        logger.warn(formatLog(message, pairs.toMap()))
    }

    fun error(message: String, throwable: Throwable? = null, vararg pairs: Pair<String, Any?>) {
        val data = pairs.toMap().toMutableMap()
        throwable?.let {
            data["error_type"] = it::class.simpleName
            data["error_message"] = it.message
            // 本番環境ではスタックトレースを含めない
            if (System.getenv("APP_ENV") == "development") {
                data["stack_trace"] = it.stackTraceToString()
            }
        }
        logger.error(formatLog(message, data))
    }

    fun security(event: String, vararg pairs: Pair<String, Any?>) {
        val data = pairs.toMap().toMutableMap()
        data["log_type"] = "SECURITY"
        logger.warn(formatLog(event, data))
    }

    private fun formatLog(message: String, data: Map<String, Any?>): String {
        val logData = data.toMutableMap()
        logData["message"] = message
        logData["timestamp"] = Instant.now().toString()
        return json.encodeToString(logData.filterValues { it != null })
    }
}

// 使用例
StructuredLogger.info(
    "User logged in",
    "user_id" to userId,
    "ip_address" to ipAddress
)

StructuredLogger.security(
    "Failed login attempt",
    "email" to email,
    "ip_address" to ipAddress,
    "attempt_count" to attemptCount
)
```

### センシティブデータのマスキング

```kotlin
// infrastructure/logging/DataMasker.kt
object DataMasker {
    private val sensitiveFields = setOf(
        "password", "token", "secret", "api_key", "apiKey",
        "authorization", "credit_card", "creditCard", "ssn"
    )

    fun mask(data: Map<String, Any?>): Map<String, Any?> {
        return data.mapValues { (key, value) ->
            when {
                key.lowercase() in sensitiveFields -> "***MASKED***"
                value is String && key.lowercase().contains("email") -> maskEmail(value)
                value is Map<*, *> -> mask(value as Map<String, Any?>)
                else -> value
            }
        }
    }

    fun maskEmail(email: String): String {
        val parts = email.split("@")
        if (parts.size != 2) return "***@***"

        val local = parts[0]
        val domain = parts[1]

        val maskedLocal = if (local.length > 2) {
            "${local.take(2)}${"*".repeat(local.length - 2)}"
        } else {
            "*".repeat(local.length)
        }

        return "$maskedLocal@$domain"
    }
}
```

---

## シークレット管理

### 環境変数による管理

```kotlin
// config/AppConfig.kt
data class AppConfig(
    val database: DatabaseConfig,
    val jwt: JwtConfig,
    val security: SecurityConfig
) {
    companion object {
        fun fromEnvironment(): AppConfig {
            return AppConfig(
                database = DatabaseConfig(
                    url = requireEnv("DATABASE_URL"),
                    user = requireEnv("DATABASE_USER"),
                    password = requireEnv("DATABASE_PASSWORD"),
                    maxPoolSize = getEnvInt("DATABASE_POOL_SIZE", 10)
                ),
                jwt = JwtConfig(
                    secret = requireEnv("JWT_SECRET"),
                    issuer = getEnv("JWT_ISSUER", "appmaster"),
                    audience = getEnv("JWT_AUDIENCE", "appmaster-api"),
                    accessTokenExpiryMinutes = getEnvInt("JWT_ACCESS_EXPIRY_MINUTES", 15),
                    refreshTokenExpiryDays = getEnvInt("JWT_REFRESH_EXPIRY_DAYS", 7)
                ),
                security = SecurityConfig(
                    allowedOrigins = requireEnv("ALLOWED_ORIGINS").split(","),
                    bcryptCost = getEnvInt("BCRYPT_COST", 12),
                    rateLimitPerMinute = getEnvInt("RATE_LIMIT_PER_MINUTE", 60)
                )
            )
        }

        private fun requireEnv(name: String): String {
            return System.getenv(name)
                ?: throw IllegalStateException("Required environment variable '$name' not set")
        }

        private fun getEnv(name: String, default: String): String {
            return System.getenv(name) ?: default
        }

        private fun getEnvInt(name: String, default: Int): Int {
            return System.getenv(name)?.toIntOrNull() ?: default
        }
    }
}

data class DatabaseConfig(
    val url: String,
    val user: String,
    val password: String,
    val maxPoolSize: Int
)

data class JwtConfig(
    val secret: String,
    val issuer: String,
    val audience: String,
    val accessTokenExpiryMinutes: Int,
    val refreshTokenExpiryDays: Int
)

data class SecurityConfig(
    val allowedOrigins: List<String>,
    val bcryptCost: Int,
    val rateLimitPerMinute: Int
)
```

### 環境変数テンプレート

```bash
# .env.example（コミットする）
# コピーして .env を作成し、実際の値を設定

# Database
DATABASE_URL=jdbc:postgresql://localhost:5432/appmaster
DATABASE_USER=postgres
DATABASE_PASSWORD=your_password_here
DATABASE_POOL_SIZE=10

# JWT
JWT_SECRET=your_32_character_secret_key_here
JWT_ISSUER=appmaster
JWT_AUDIENCE=appmaster-api
JWT_ACCESS_EXPIRY_MINUTES=15
JWT_REFRESH_EXPIRY_DAYS=7

# Security
ALLOWED_ORIGINS=http://localhost:3000,http://localhost:8080
CORS_ALLOWED_HOSTS=https://app.example.com,https://admin.example.com
BCRYPT_COST=12
RATE_LIMIT_PER_MINUTE=60

# Application
APP_ENV=development
KTOR_DEVELOPMENT=true
LOG_LEVEL=INFO
```

### 本番環境での必須設定

**Critical:** 以下の環境変数は本番環境で必ず設定すること。未設定の場合はアプリケーション起動時にエラーとなるように実装する。

| 環境変数 | 要件 | 説明 |
|---------|------|------|
| `JWT_SECRET` | 32文字以上 | JWT 署名用シークレット |
| `CORS_ALLOWED_HOSTS` | カンマ区切りホスト | CORS 許可ホスト |
| `DATABASE_PASSWORD` | 必須 | データベースパスワード |

```kotlin
// security/JwtConfig.kt - 環境変数検証パターン
data class JwtConfig(
    val secret: String,
    val issuer: String,
    val audience: String,
    val realm: String,
    val accessTokenExpirationMs: Long,
    val refreshTokenExpirationMs: Long
) {
    companion object {
        private const val DEV_SECRET = "dev-secret-key-for-local-testing-only"
        private const val MIN_SECRET_LENGTH = 32

        fun from(environment: ApplicationEnvironment): JwtConfig {
            val config = environment.config
            val isDevelopment = config.propertyOrNull("ktor.development")?.getString()?.toBoolean()
                ?: System.getenv("KTOR_DEVELOPMENT")?.toBoolean()
                ?: false

            val secret = config.propertyOrNull("jwt.secret")?.getString()
                ?: System.getenv("JWT_SECRET")
                ?: if (isDevelopment) {
                    environment.log.warn("JWT: Using development secret - DO NOT use in production!")
                    DEV_SECRET
                } else {
                    throw IllegalStateException(
                        "JWT_SECRET environment variable must be set in production. " +
                        "Minimum length: $MIN_SECRET_LENGTH characters."
                    )
                }

            // Validate secret length
            if (secret.length < MIN_SECRET_LENGTH) {
                if (isDevelopment) {
                    environment.log.warn("JWT: Secret is shorter than recommended ($MIN_SECRET_LENGTH chars)")
                } else {
                    throw IllegalStateException(
                        "JWT_SECRET must be at least $MIN_SECRET_LENGTH characters long"
                    )
                }
            }

            return JwtConfig(
                secret = secret,
                issuer = config.propertyOrNull("jwt.issuer")?.getString()
                    ?: System.getenv("JWT_ISSUER")
                    ?: "appmaster-api",
                audience = config.propertyOrNull("jwt.audience")?.getString()
                    ?: System.getenv("JWT_AUDIENCE")
                    ?: "appmaster-client",
                realm = "appmaster",
                accessTokenExpirationMs = 15 * 60 * 1000L,  // 15 minutes
                refreshTokenExpirationMs = 7 * 24 * 60 * 60 * 1000L  // 7 days
            )
        }
    }
}
```

### 本番環境向け CORS 設定

開発環境では `anyHost()` を許可するが、本番環境では明示的なホスト指定を必須とする。

```kotlin
// plugins/Cors.kt
fun Application.configureCors() {
    val allowedHosts = environment.config.propertyOrNull("cors.allowedHosts")?.getList()
        ?: System.getenv("CORS_ALLOWED_HOSTS")?.split(",")
        ?: emptyList()

    val isDevelopment = developmentMode
    val appEnvironment = environment

    install(CORS) {
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Patch)
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Accept)

        if (isDevelopment && allowedHosts.isEmpty()) {
            // Development mode: allow any host for local testing
            anyHost()
            appEnvironment.log.warn("CORS: anyHost() enabled - DO NOT use in production!")
        } else if (allowedHosts.isNotEmpty()) {
            // Production mode: only allow specified hosts
            allowedHosts.forEach { host ->
                val trimmed = host.trim()
                if (trimmed.startsWith("https://")) {
                    allowHost(trimmed.removePrefix("https://"), schemes = listOf("https"))
                } else if (trimmed.startsWith("http://")) {
                    allowHost(trimmed.removePrefix("http://"), schemes = listOf("http"))
                } else {
                    allowHost(trimmed, schemes = listOf("https"))
                }
            }
            appEnvironment.log.info("CORS: Allowed hosts: $allowedHosts")
        } else {
            // Production without configured hosts - fail safe
            appEnvironment.log.error("CORS: No allowed hosts configured in production mode!")
            throw IllegalStateException("CORS_ALLOWED_HOSTS must be configured in production")
        }
    }
}
```

### RefreshToken Repository のドメイン層配置

Repository インターフェースはドメイン層に配置し、データ層への直接依存を避ける。

```kotlin
// domain/repository/RefreshTokenRepository.kt
interface RefreshTokenRepository {
    suspend fun save(token: RefreshToken): Result<Unit>
    suspend fun findByToken(token: String): Result<RefreshToken>
    suspend fun revokeByToken(token: String): Result<Unit>
    suspend fun revokeAllByUserId(userId: UUID): Result<Unit>
}

// domain/model/RefreshToken.kt
data class RefreshToken(
    val id: UUID,
    val userId: UUID,
    val token: String,
    val expiresAt: Long,
    val revoked: Boolean
)
```

---

## データベースセキュリティ

### 接続設定

```kotlin
// plugins/Database.kt
fun Application.configureDatabase(config: DatabaseConfig) {
    Database.connect(
        url = config.url,
        driver = "org.postgresql.Driver",
        user = config.user,
        password = config.password,
        setupConnection = { connection ->
            // SSL/TLS 接続を強制（本番環境）
            if (System.getenv("APP_ENV") == "production") {
                connection.createStatement().execute("SET ssl = on")
            }
        },
        databaseConfig = org.jetbrains.exposed.sql.DatabaseConfig {
            // SQLログ出力（開発環境のみ）
            sqlLogger = if (System.getenv("APP_ENV") == "development") {
                StdOutSqlLogger
            } else {
                Slf4jSqlDebugLogger
            }
        }
    )

    // コネクションプール設定
    val hikariConfig = HikariConfig().apply {
        jdbcUrl = config.url
        username = config.user
        password = config.password
        maximumPoolSize = config.maxPoolSize
        minimumIdle = 2
        idleTimeout = 60000 // 1分
        connectionTimeout = 30000 // 30秒
        maxLifetime = 1800000 // 30分

        // SSL設定（本番環境）
        if (System.getenv("APP_ENV") == "production") {
            addDataSourceProperty("ssl", "true")
            addDataSourceProperty("sslmode", "require")
        }
    }

    Database.connect(HikariDataSource(hikariConfig))
}
```

### データアクセス制御

```kotlin
// data/repository/UserRepositoryImpl.kt
class UserRepositoryImpl : UserRepository {

    override suspend fun findById(id: UserId): User? = dbQuery {
        // Exposed のパラメータバインディングを使用
        UserTable.select { UserTable.id eq id.value }
            .map { it.toUser() }
            .singleOrNull()
    }

    override suspend fun update(user: User): User = dbQuery {
        // 更新対象のフィールドのみ更新（過剰な更新を防止）
        UserTable.update({ UserTable.id eq user.id.value }) {
            it[name] = user.name.value
            it[email] = user.email.value
            it[updatedAt] = Instant.now()
        }
        user
    }
}

// トランザクション管理
suspend fun <T> dbQuery(block: suspend () -> T): T =
    newSuspendedTransaction(Dispatchers.IO) { block() }
```

---

## セキュリティチェックリスト

実装時に以下を確認してください。

### 認証・認可
- [ ] すべての保護エンドポイントで認証を要求
- [ ] リソースアクセス時に所有者/権限チェック
- [ ] JWT 署名アルゴリズムを適切に設定（RS256 推奨）
- [ ] トークン有効期限を適切に設定
- [ ] リフレッシュトークンを安全に保存・検証

### 暗号化
- [ ] パスワードは BCrypt でハッシュ化（コスト係数 12 以上）
- [ ] 機密データは TLS 1.2 以上で送信
- [ ] 暗号化キーを環境変数または KMS で管理

### HTTP セキュリティ
- [ ] セキュリティヘッダーを設定
- [ ] CORS を適切に設定
- [ ] HTTPS を強制（HSTS）

### 入力バリデーション
- [ ] すべての入力を検証
- [ ] リクエストサイズを制限
- [ ] ファイルアップロードを制限（該当する場合）

### レート制限
- [ ] API エンドポイントにレート制限を設定
- [ ] 認証エンドポイントにより厳しい制限を設定

### ロギング
- [ ] セキュリティイベントをログに記録
- [ ] 機密情報をログに出力しない
- [ ] 監査ログを実装

### 依存関係
- [ ] 定期的に依存関係を更新
- [ ] 脆弱性スキャンを CI に組み込む
