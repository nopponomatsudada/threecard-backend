---
name: security-audit
description: >
  バックエンドコードのセキュリティ監査を行うスキル。
  OWASP Top 10 に基づいた脆弱性検出、セキュリティ設定の検証、
  依存関係の脆弱性チェックを実施。
  トリガー: "セキュリティ監査", "脆弱性チェック", "security audit", "OWASP チェック"
---

# Security Audit Skill

バックエンドコードのセキュリティ監査を実施するスキル。

## 監査手順

### Phase 1: 認証・認可の監査

```bash
# 認証なしエンドポイントの検出
grep -rn "route\|get\|post\|put\|delete" --include="*.kt" src/main/kotlin/routes/ | grep -v "authenticate"
```

**チェックポイント:**

1. **認証の有無**
   ```kotlin
   // ❌ 脆弱: 認証なし
   route("/api/v1/users") {
       get("/{id}") { ... }
   }

   // ✅ 安全: 認証あり
   authenticate("jwt") {
       route("/api/v1/users") {
           get("/{id}") { ... }
       }
   }
   ```

2. **所有者チェック**
   ```kotlin
   // ❌ 脆弱: 誰でもアクセス可能
   get("/users/{id}") {
       val user = getUserUseCase(call.parameters["id"]!!)
       call.respond(user)
   }

   // ✅ 安全: 所有者チェックあり
   get("/users/{id}") {
       val requestedId = call.parameters["id"]!!
       val currentUserId = call.principal<JWTPrincipal>()?.getClaim("userId")

       if (requestedId != currentUserId && !isAdmin(call)) {
           throw ForbiddenException()
       }

       val user = getUserUseCase(requestedId)
       call.respond(user)
   }
   ```

3. **ロールベースアクセス制御**
   ```kotlin
   // 管理者専用エンドポイント
   authenticate("jwt") {
       install(RoleAuthorizationPlugin) { roles = setOf(Role.ADMIN) }
       delete("/admin/users/{id}") { ... }
   }
   ```

---

### Phase 2: インジェクション脆弱性の検出

```bash
# 危険な SQL パターンの検出
grep -rn "exec\|rawQuery\|executeQuery" --include="*.kt" src/
grep -rn "\$.*SELECT\|SELECT.*\$" --include="*.kt" src/
```

**チェックポイント:**

1. **SQL インジェクション**
   ```kotlin
   // ❌ 脆弱: 文字列連結
   exec("SELECT * FROM users WHERE email = '$email'")

   // ✅ 安全: パラメータバインディング
   UserTable.select { UserTable.email eq email }
   ```

2. **コマンドインジェクション**
   ```kotlin
   // ❌ 脆弱: ユーザー入力を直接実行
   Runtime.getRuntime().exec("ls $userInput")

   // ✅ 安全: 引数を分離
   ProcessBuilder("ls", "-la", sanitizedPath).start()
   ```

---

### Phase 3: 暗号化の監査

```bash
# パスワード処理の検出
grep -rn "password\|Password" --include="*.kt" src/
```

**チェックポイント:**

1. **パスワードハッシュ化**
   ```kotlin
   // ❌ 脆弱: 平文保存
   UserTable.insert { it[password] = request.password }

   // ❌ 脆弱: 弱いハッシュ
   val hash = MessageDigest.getInstance("MD5").digest(password.toByteArray())

   // ✅ 安全: BCrypt
   val hash = BCrypt.withDefaults().hashToString(12, password.toCharArray())
   ```

2. **JWT 設定**
   ```kotlin
   // ❌ 脆弱: ハードコードされた秘密鍵
   Algorithm.HMAC256("my-secret-key")

   // ❌ 脆弱: 弱いアルゴリズム
   Algorithm.none()

   // ✅ 安全: 環境変数 + 適切なアルゴリズム
   Algorithm.HMAC256(System.getenv("JWT_SECRET"))
   // または本番では RS256
   Algorithm.RSA256(publicKey, privateKey)
   ```

---

### Phase 4: HTTP セキュリティの監査

```bash
# セキュリティヘッダー設定の確認
grep -rn "DefaultHeaders\|X-Frame-Options\|Content-Security-Policy" --include="*.kt" src/
```

**チェックポイント:**

1. **セキュリティヘッダー**
   ```kotlin
   install(DefaultHeaders) {
       header("X-Frame-Options", "DENY")
       header("X-Content-Type-Options", "nosniff")
       header("X-XSS-Protection", "1; mode=block")
       header("Strict-Transport-Security", "max-age=31536000; includeSubDomains")
       header("Content-Security-Policy", "default-src 'self'")
   }
   ```

2. **CORS 設定**
   ```kotlin
   // ❌ 脆弱: ワイルドカード
   install(CORS) {
       anyHost()
   }

   // ✅ 安全: 明示的なオリジン指定
   install(CORS) {
       allowHost("app.example.com", schemes = listOf("https"))
       allowCredentials = true
   }
   ```

---

### Phase 5: ロギング・機密情報の監査

```bash
# ログ出力の検出
grep -rn "logger\.\|log\.\|println" --include="*.kt" src/
```

**チェックポイント:**

1. **機密情報のログ出力**
   ```kotlin
   // ❌ 脆弱: パスワード/トークンをログ出力
   logger.info("Login: email=$email, password=$password")
   logger.debug("Token: $accessToken")

   // ✅ 安全: 機密情報をマスク
   logger.info("Login: email=$email, ip=$ipAddress")
   ```

2. **エラーメッセージ**
   ```kotlin
   // ❌ 脆弱: 詳細なエラー情報
   call.respond(HttpStatusCode.InternalServerError, e.stackTraceToString())

   // ✅ 安全: 汎用エラーメッセージ
   call.respond(HttpStatusCode.InternalServerError,
       ErrorResponse("INTERNAL_ERROR", "An error occurred"))
   ```

---

### Phase 6: 依存関係の脆弱性チェック

```bash
# OWASP Dependency Check の実行
./gradlew dependencyCheckAnalyze

# または手動で確認
./gradlew dependencies | grep -E "(log4j|jackson|snakeyaml)"
```

**既知の脆弱性:**

| ライブラリ | CVE | 対策 |
|-----------|-----|------|
| Log4j < 2.17.0 | CVE-2021-44228 | 2.17.1 以上に更新 |
| Jackson < 2.12.6 | CVE-2020-36518 | 最新版に更新 |
| SnakeYAML < 1.32 | CVE-2022-25857 | 最新版に更新 |

---

## 監査レポートフォーマット

```markdown
# セキュリティ監査レポート

**監査日時**: YYYY-MM-DD HH:MM
**対象**: app-master-backend
**監査者**: Claude Code

## サマリー

| 重要度 | 件数 |
|--------|------|
| Critical | X |
| High | X |
| Medium | X |
| Low | X |

## 検出された脆弱性

### Critical

#### [A01] 認証バイパス
- **ファイル**: `src/main/kotlin/routes/UserRoutes.kt:45`
- **問題**: `/api/v1/users/{id}` に認証がない
- **影響**: 任意のユーザー情報にアクセス可能
- **修正**: `authenticate("jwt")` ブロックで囲む

### High

#### [A07] レート制限なし
- **ファイル**: `src/main/kotlin/routes/AuthRoutes.kt:12`
- **問題**: ログインエンドポイントにレート制限がない
- **影響**: ブルートフォース攻撃のリスク
- **修正**: `rateLimit(RateLimitName("auth"))` を追加

## 推奨事項

1. **即時対応**: Critical/High の脆弱性を修正
2. **短期**: セキュリティヘッダーの追加
3. **中期**: 監査ログの実装
4. **長期**: 定期的なセキュリティ監査の実施

## 付録

### 監査対象ファイル
- src/main/kotlin/routes/*.kt
- src/main/kotlin/plugins/*.kt
- src/main/kotlin/domain/**/*.kt
- src/main/kotlin/data/**/*.kt
```

---

## 自動監査スクリプト

```bash
#!/bin/bash
# scripts/security-audit.sh

echo "=== Security Audit ==="
echo ""

echo "## Phase 1: Authentication Check"
echo "Endpoints without authentication:"
grep -rn "route\|get\|post\|put\|delete" --include="*.kt" src/main/kotlin/routes/ | grep -v "authenticate" | head -20

echo ""
echo "## Phase 2: SQL Injection Check"
echo "Potential SQL injection patterns:"
grep -rn "exec\|rawQuery\|\$.*SELECT" --include="*.kt" src/ | head -10

echo ""
echo "## Phase 3: Password Handling Check"
echo "Password-related code:"
grep -rn "password" --include="*.kt" src/main/kotlin/data/ | head -10

echo ""
echo "## Phase 4: Logging Check"
echo "Potential sensitive data logging:"
grep -rn "logger.*password\|logger.*token\|log.*password\|log.*token" --include="*.kt" src/ | head -10

echo ""
echo "## Phase 5: Dependency Check"
if [ -f "build.gradle.kts" ]; then
    echo "Running OWASP Dependency Check..."
    ./gradlew dependencyCheckAnalyze --info 2>/dev/null || echo "Dependency check plugin not configured"
fi

echo ""
echo "=== Audit Complete ==="
```

---

## OWASP Top 10 クイックリファレンス

| ID | 脆弱性 | 検出方法 | 対策 |
|----|--------|----------|------|
| A01 | Access Control | 認証なしエンドポイント検出 | authenticate + 所有者チェック |
| A02 | Crypto Failures | パスワード処理検査 | BCrypt, RS256 |
| A03 | Injection | SQL パターン検出 | パラメータバインディング |
| A04 | Insecure Design | アーキテクチャレビュー | Clean Architecture |
| A05 | Misconfiguration | ヘッダー/CORS 検査 | セキュリティヘッダー設定 |
| A06 | Vulnerable Components | 依存関係スキャン | OWASP Dependency Check |
| A07 | Auth Failures | レート制限検査 | RateLimit プラグイン |
| A08 | Integrity Failures | CI/CD 検査 | 依存関係ロック |
| A09 | Logging Failures | ログ出力検査 | 機密情報マスキング |
| A10 | SSRF | URL 処理検査 | ホワイトリスト検証 |

---

## Reference

- [セキュリティガイド](../../../docs/guides/security.md)
- [OWASP Top 10](https://owasp.org/Top10/)
- [OWASP Cheat Sheet Series](https://cheatsheetseries.owasp.org/)
- [Ktor Security](https://ktor.io/docs/security.html)
