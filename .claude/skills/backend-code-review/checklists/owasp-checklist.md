# OWASP Top 10 チェックリスト

コードレビュー時に使用する OWASP Top 10 (2021) 対応チェックリスト。

## A01:2021 - Broken Access Control

### 必須チェック

- [ ] すべての保護リソースで認証を要求
- [ ] リソースアクセス時に所有者/権限チェック
- [ ] 管理者機能にロールベースアクセス制御
- [ ] ID の予測可能性を排除（UUID 使用）
- [ ] CORS 設定が適切

### コード例

```kotlin
// 所有者チェック
val currentUserId = call.principal<JWTPrincipal>()?.getClaim("userId")
if (resource.ownerId != currentUserId && !hasRole(Role.ADMIN)) {
    throw ForbiddenException()
}

// ロールチェック
install(RoleAuthorizationPlugin) { roles = setOf(Role.ADMIN) }
```

---

## A02:2021 - Cryptographic Failures

### 必須チェック

- [ ] パスワードは BCrypt (コスト 12+) でハッシュ化
- [ ] JWT 署名は RS256 (本番) / HS256 (開発)
- [ ] 機密データの暗号化 (AES-256-GCM)
- [ ] TLS 1.2 以上を使用
- [ ] 暗号化キーを環境変数/KMS で管理

### コード例

```kotlin
// パスワードハッシュ化
val hash = BCrypt.withDefaults().hashToString(12, password.toCharArray())

// JWT 署名
JWT.create()
    .sign(Algorithm.HMAC256(secret))  // 開発
    // .sign(Algorithm.RSA256(publicKey, privateKey))  // 本番
```

---

## A03:2021 - Injection

### 必須チェック

- [ ] ORM パラメータバインディングを使用
- [ ] 生 SQL クエリの禁止
- [ ] 外部コマンド実行時の引数エスケープ
- [ ] LDAP/XPath クエリのパラメータ化

### コード例

```kotlin
// ✅ Exposed パラメータバインディング
UserTable.select { UserTable.email eq email }

// ❌ 禁止: 文字列連結
exec("SELECT * FROM users WHERE email = '$email'")
```

---

## A04:2021 - Insecure Design

### 必須チェック

- [ ] Clean Architecture による関心の分離
- [ ] 設計段階でセキュリティ要件を定義
- [ ] 脅威モデリングの実施
- [ ] セキュリティテストの計画

### 設計原則

```
Routes → Domain ← Data
(Domain 層は外部依存なし)
```

---

## A05:2021 - Security Misconfiguration

### 必須チェック

- [ ] セキュリティヘッダー設定
- [ ] CORS の適切な設定
- [ ] 本番でデバッグモード無効
- [ ] エラーメッセージで詳細情報を漏らさない
- [ ] 不要な機能/エンドポイントを無効化

### コード例

```kotlin
install(DefaultHeaders) {
    header("X-Frame-Options", "DENY")
    header("X-Content-Type-Options", "nosniff")
    header("Strict-Transport-Security", "max-age=31536000")
    header("Content-Security-Policy", "default-src 'self'")
}
```

---

## A06:2021 - Vulnerable and Outdated Components

### 必須チェック

- [ ] 依存関係の定期更新
- [ ] OWASP Dependency Check の CI 組み込み
- [ ] 既知の脆弱性モニタリング
- [ ] 未使用の依存関係を削除

### 設定例

```kotlin
// build.gradle.kts
plugins {
    id("org.owasp.dependencycheck") version "8.4.0"
}

dependencyCheck {
    failBuildOnCVSS = 7.0f
}
```

---

## A07:2021 - Identification and Authentication Failures

### 必須チェック

- [ ] ログイン試行のレート制限
- [ ] パスワード強度要件
- [ ] トークン有効期限の設定
- [ ] ログアウト時のトークン無効化
- [ ] MFA の検討

### コード例

```kotlin
// レート制限
rateLimit(RateLimitName("auth")) {
    post("/auth/login") { ... }
}

// パスワード要件
Password.create(value)  // 8文字以上、大文字/小文字/数字必須
```

---

## A08:2021 - Software and Data Integrity Failures

### 必須チェック

- [ ] Gradle 依存関係ロック
- [ ] CI/CD パイプラインのアクセス制御
- [ ] デプロイ成果物の署名
- [ ] 信頼できるソースからの依存関係取得

### 設定例

```kotlin
// gradle.lockfile の使用
dependencyLocking {
    lockAllConfigurations()
}
```

---

## A09:2021 - Security Logging and Monitoring Failures

### 必須チェック

- [ ] 認証イベントのログ記録
- [ ] 機密情報のマスキング
- [ ] 監査ログの実装
- [ ] セキュリティアラートの設定

### コード例

```kotlin
// 監査ログ
auditService.log(
    action = AuditAction.LOGIN_SUCCESS,
    resource = "auth",
    userId = userId,
    ipAddress = call.request.origin.remoteHost
)

// 機密情報マスキング
logger.info("Login: email=${maskEmail(email)}, ip=$ip")
```

---

## A10:2021 - Server-Side Request Forgery (SSRF)

### 必須チェック

- [ ] 外部 URL のホワイトリスト検証
- [ ] 内部ネットワークアドレスのブロック
- [ ] クラウドメタデータ URL のブロック
- [ ] 許可スキームの制限 (http/https のみ)

### コード例

```kotlin
fun validateUrl(url: String): Boolean {
    val uri = URI(url)
    val blocked = listOf("localhost", "127.0.0.1", "169.254.169.254")

    if (uri.host in blocked) return false
    if (uri.scheme !in listOf("http", "https")) return false

    return true
}
```

---

## クイックリファレンス

| ID | 脆弱性 | 重要度 | 主な対策 |
|----|--------|--------|----------|
| A01 | Access Control | Critical | 認証/認可チェック |
| A02 | Crypto Failures | Critical | BCrypt, RS256, TLS |
| A03 | Injection | Critical | パラメータバインディング |
| A04 | Insecure Design | High | Clean Architecture |
| A05 | Misconfiguration | High | セキュリティヘッダー |
| A06 | Vulnerable Components | Medium | 依存関係スキャン |
| A07 | Auth Failures | High | レート制限 |
| A08 | Integrity Failures | Medium | 依存関係ロック |
| A09 | Logging Failures | Medium | 監査ログ |
| A10 | SSRF | Medium | URL ホワイトリスト |
