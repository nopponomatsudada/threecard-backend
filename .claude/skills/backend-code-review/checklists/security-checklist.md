# セキュリティチェックリスト

実装時および PR レビュー時に使用するセキュリティチェックリスト。

## 認証・認可

### 認証

- [ ] すべての保護エンドポイントで `authenticate("jwt")` を使用
- [ ] JWT 署名アルゴリズムが適切（RS256 推奨）
- [ ] アクセストークン有効期限: 15分以下
- [ ] リフレッシュトークン有効期限: 7日以下
- [ ] ログアウト時にリフレッシュトークンを無効化

### 認可

- [ ] リソースアクセス時に所有者チェック
- [ ] 管理者機能にロールチェック
- [ ] 水平権限昇格の防止
- [ ] 垂直権限昇格の防止

```kotlin
// チェック例
val currentUserId = call.principal<JWTPrincipal>()?.getClaim("userId")
if (resource.ownerId != currentUserId) {
    throw ForbiddenException()
}
```

---

## 暗号化

### パスワード

- [ ] BCrypt でハッシュ化
- [ ] コスト係数 12 以上
- [ ] 平文パスワードをログ出力しない
- [ ] 平文パスワードをデータベースに保存しない

### JWT

- [ ] 秘密鍵を環境変数で管理
- [ ] 本番環境で RS256 を使用
- [ ] 適切な有効期限を設定
- [ ] `jti` (JWT ID) でリプレイ攻撃を防止

### データ暗号化

- [ ] 機密データは AES-256-GCM で暗号化
- [ ] 暗号化キーを KMS / Vault で管理
- [ ] IV（初期化ベクトル）をランダム生成

---

## HTTP セキュリティ

### セキュリティヘッダー

- [ ] `X-Frame-Options: DENY`
- [ ] `X-Content-Type-Options: nosniff`
- [ ] `X-XSS-Protection: 1; mode=block`
- [ ] `Strict-Transport-Security: max-age=31536000`
- [ ] `Content-Security-Policy: default-src 'self'`
- [ ] `Referrer-Policy: strict-origin-when-cross-origin`

### CORS

- [ ] 許可オリジンを明示的に指定
- [ ] ワイルドカード (`*`) を使用しない（本番）
- [ ] `allowCredentials` の適切な設定
- [ ] プリフライトキャッシュの設定

### HTTPS

- [ ] TLS 1.2 以上を使用
- [ ] HSTS ヘッダーを設定
- [ ] HTTP → HTTPS リダイレクト

---

## 入力バリデーション

### 基本

- [ ] すべての入力を検証
- [ ] ValueObject でドメインルールを適用
- [ ] 最大長の制限
- [ ] 形式の検証（メール、電話番号等）

### リクエスト制限

- [ ] リクエストボディサイズ制限（1MB 推奨）
- [ ] ファイルアップロードサイズ制限
- [ ] ファイルタイプの制限
- [ ] JSON ネスト深度の制限

```kotlin
// バリデーション例
@JvmInline
value class Email private constructor(val value: String) {
    companion object {
        fun create(value: String): Result<Email> {
            if (!EMAIL_REGEX.matches(value)) {
                return Result.failure(ValidationError("Invalid email"))
            }
            return Result.success(Email(value))
        }
    }
}
```

---

## データベース

### SQL インジェクション対策

- [ ] Exposed パラメータバインディングを使用
- [ ] 生 SQL クエリを避ける
- [ ] 動的カラム名/テーブル名を避ける

### 接続セキュリティ

- [ ] SSL/TLS 接続を使用（本番）
- [ ] 接続タイムアウトを設定
- [ ] コネクションプールサイズを制限
- [ ] 最小権限の原則（DB ユーザー権限）

```kotlin
// 安全なクエリ
UserTable.select { UserTable.email eq email }

// 危険なクエリ（禁止）
exec("SELECT * FROM users WHERE email = '$email'")
```

---

## レート制限

### エンドポイント別制限

| エンドポイント | 制限 |
|--------------|------|
| 認証（ログイン/登録） | 5回/分 |
| API 全般 | 60回/分 |
| ファイルアップロード | 10回/分 |

### 実装

- [ ] IP ベースのレート制限
- [ ] ユーザーベースのレート制限
- [ ] 429 レスポンスの適切な処理
- [ ] `Retry-After` ヘッダーの設定

```kotlin
install(RateLimit) {
    register(RateLimitName("auth")) {
        rateLimiter(limit = 5, refillPeriod = 1.minutes)
    }
}
```

---

## ロギング・監査

### ログ出力

- [ ] 認証イベントをログに記録
- [ ] エラー/例外をログに記録
- [ ] リクエスト ID を含める
- [ ] 構造化ログ形式を使用

### 機密情報の保護

- [ ] パスワードをログに出力しない
- [ ] トークンをログに出力しない
- [ ] 個人情報をマスキング
- [ ] スタックトレースを本番で出力しない

### 監査ログ

- [ ] ユーザー認証イベント
- [ ] データ変更操作
- [ ] 管理者操作
- [ ] セキュリティイベント

```kotlin
// 禁止
logger.info("Login: password=$password")

// 推奨
logger.info("Login: userId=$userId, ip=$ip")
auditService.log(AuditAction.LOGIN_SUCCESS, userId, ip)
```

---

## シークレット管理

### 環境変数

- [ ] すべてのシークレットを環境変数で管理
- [ ] `.env` ファイルを `.gitignore` に追加
- [ ] `.env.example` をコミット（値なし）
- [ ] 本番では KMS / Vault を使用

### 必須の環境変数

| 変数名 | 説明 |
|--------|------|
| `DATABASE_URL` | DB 接続 URL |
| `DATABASE_PASSWORD` | DB パスワード |
| `JWT_SECRET` | JWT 署名キー |
| `ALLOWED_ORIGINS` | CORS 許可オリジン |

---

## 依存関係

### 脆弱性管理

- [ ] OWASP Dependency Check を CI に組み込み
- [ ] CVSS 7.0 以上で失敗
- [ ] 定期的な依存関係更新
- [ ] 未使用の依存関係を削除

### 既知の脆弱性

以下のライブラリは特に注意:

- Log4j (CVE-2021-44228)
- Jackson (デシリアライゼーション)
- SnakeYAML (任意コード実行)

---

## チェックリストサマリー

### Critical (必須)

- [ ] 認証・認可の実装
- [ ] パスワードのハッシュ化
- [ ] SQL インジェクション対策
- [ ] 機密情報のログ出力禁止
- [ ] **FK に `onDelete = ReferenceOption.CASCADE` を指定**（未指定だとユーザー削除で 500 エラー）
- [ ] **Webhook トークン比較は `MessageDigest.isEqual()` で定数時間比較**
- [ ] **DB パスワード等のハードコード禁止**（開発用フォールバックは `isDev` ガード付きのみ）
- [ ] **`isLenient = false`** をデフォルトに（不正 JSON 受け入れ防止）

### High (強く推奨)

- [ ] セキュリティヘッダー
- [ ] CORS 設定（本番 fail-fast: `CORS_ALLOWED_HOSTS` 未設定で起動拒否）
- [ ] 入力バリデーション（description max 5000, URL max 2000 等）
- [ ] レート制限（auth/sensitive/api の 3 ティア分離）
- [ ] **画像アップロード時の magic bytes 検証**（MIME タイプだけでなくファイルヘッダーも確認）
- [ ] **サブリソース DELETE のオーナーシップ検証**（SubResource → ParentResource → User の検証チェーン）

### Medium (推奨)

- [ ] 監査ログ
- [ ] 依存関係スキャン
- [ ] 構造化ログ
- [ ] `prettyPrint = isDev`（本番では JSON を圧縮）
- [ ] 機密トークン等はレスポンスでマスク化（`token.take(8) + "***"`）

### Docker ローカル開発チェック

- [ ] Dockerfile のビルドコマンドが build.gradle.kts と一致（`buildFatJar` vs `shadowJar`）
- [ ] docker-compose.yml に `KTOR_DEVELOPMENT`, `CORS_ALLOWED_HOSTS`, `KTOR_OPTS` を設定
- [ ] ENTRYPOINT に `$KTOR_OPTS` を含める（JVM システムプロパティ渡し用）
