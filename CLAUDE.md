# AppMaster Backend - Claude Code ガイド

## プロジェクト概要

このリポジトリは、AppMaster プロジェクトのバックエンド API サーバーです。Kotlin + Ktor を使用したクリーンアーキテクチャベースの REST API を提供します。

## 技術スタック

| 役割 | 技術 |
|------|------|
| 言語 | Kotlin |
| フレームワーク | Ktor 3.x |
| 認証 | Ktor Auth (JWT) |
| データベース | Exposed (Kotlin ORM) + PostgreSQL |
| シリアライゼーション | kotlinx.serialization |
| DI | Koin |
| テスト | Kotest + Ktor Test |
| コンテナ | Docker + Docker Compose |

## ディレクトリ構成

```
appmaster-backend/
├── src/
│   └── main/kotlin/
│       ├── Application.kt           # エントリーポイント
│       ├── plugins/                  # Ktor プラグイン設定
│       │   ├── Routing.kt
│       │   ├── Serialization.kt
│       │   ├── Authentication.kt
│       │   └── Database.kt
│       ├── domain/                   # ドメイン層（ビジネスロジック）
│       │   ├── model/                # エンティティ、値オブジェクト
│       │   ├── repository/           # Repository インターフェース
│       │   ├── usecase/              # ユースケース
│       │   └── error/                # ドメインエラー
│       ├── data/                     # データ層
│       │   ├── entity/               # Exposed テーブル定義
│       │   ├── dao/                  # データアクセス
│       │   └── repository/           # Repository 実装
│       └── routes/                   # API エンドポイント
│           ├── AuthRoutes.kt
│           ├── UserRoutes.kt
│           └── dto/                  # リクエスト/レスポンス DTO
├── src/test/kotlin/                  # テストコード
├── api-spec/                        # OpenAPI 仕様
│   └── openapi.yml                  # API 定義（モックサーバーの SSOT）
├── mock/                            # モックサーバー
│   ├── docker-compose.yml           # Prism モックサーバー設定
│   └── data/                        # カスタムモックデータ
├── infra/                           # インフラストラクチャ
│   ├── docker/                      # Docker関連
│   │   ├── Dockerfile               # アプリケーションコンテナ
│   │   ├── docker-compose.yml       # ローカル開発用
│   │   └── docker-compose.prod.yml  # 本番参照用
│   ├── terraform/                   # AWS IaC
│   │   ├── modules/                 # 再利用可能モジュール
│   │   └── environments/            # 環境別設定 (dev/staging/prod)
│   └── scripts/                     # デプロイスクリプト
├── docs/                            # ドキュメント
│   ├── guides/                      # アーキテクチャガイド
│   └── module-scaffolding.md        # 実装手順書
├── .claude/
│   └── skills/                      # AI Skills
├── build.gradle.kts
└── gradle.properties
```

## アーキテクチャ概要

### レイヤー構成

```
┌─────────────────────────────────────────────────┐
│                   Routes 層                      │
│         (API エンドポイント、DTO 変換)            │
└─────────────────────────────────────────────────┘
                      │
                      ▼
┌─────────────────────────────────────────────────┐
│                  Domain 層                       │
│    (UseCase、Entity、Repository インターフェース)  │
└─────────────────────────────────────────────────┘
                      │
                      ▼
┌─────────────────────────────────────────────────┐
│                   Data 層                        │
│      (Repository 実装、Exposed Entity、DAO)       │
└─────────────────────────────────────────────────┘
```

### 依存関係ルール

- **Routes → Domain**: Routes は Domain に依存可能
- **Data → Domain**: Data は Domain に依存可能
- **Domain → なし**: Domain 層は他のレイヤーに依存しない（Pure Kotlin）
- **Routes ↛ Data**: Routes は Data に直接依存してはいけない

## AI 開発ワークフロー

### フェーズ 1-2: 要件定義・仕様作成

親プロジェクト（appmaster-parent）の `docs/` を参照:
- `docs/requirements/templates/feature-requirements.md`
- `docs/spec-template.md`

### フェーズ 3: アーキテクチャ理解

このリポジトリの `docs/guides/` を順に読む:

1. `architecture-guidelines.md` - 全体方針
2. `domain-layer.md` - ドメイン層の設計
3. `data-layer.md` - データ層の設計
4. `routes-layer.md` - API エンドポイントの設計
5. `error-handling.md` - エラーハンドリング
6. `security.md` - セキュリティガイド（OWASP Top 10 対応）
7. `testing-guidelines.md` - テスト戦略

### フェーズ 4: 実装

`docs/module-scaffolding.md` を参照し、以下の順で実装:

0. **依存関係チェック（初回のみ）**: `/backend-dependency-audit` で脆弱性・バージョンを確認し、必要に応じてアップグレード
1. **Domain 層**: Entity, ValueObject, Repository インターフェース, UseCase
2. **Data 層**: Exposed Entity, DAO, Repository 実装
3. **Routes 層**: DTO, エンドポイント
4. **DI 設定**: Koin Module
5. **テスト**: UseCase テスト, Repository テスト, API テスト

> **重要**: 初回実装セッション開始時に `gradle/libs.versions.toml` のバージョンを確認すること。
> メジャーバージョンの破壊的変更は実装後だと全ファイルに影響するため、必ず実装前にアップグレードする。

**コミット方針**: レイヤ単位、機能単位など適切な粒度で作業完了ごとにコミット。

## 利用可能な Agent Skills

### 自動起動 Skills

| Skill | トリガー | 説明 |
|-------|---------|------|
| architecture-guide | 機能実装時 | Clean Architecture リファレンス |
| module-scaffolding | "新しいエンティティ", "API追加" | 新規機能の実装ガイド |
| backend-code-review | "レビュー", "PRチェック" | コード品質レビュー（OWASP Top 10 対応） |
| template-code-generation | "Entity生成", "テンプレート" | コード自動生成 |
| dependency-audit | "依存関係チェック" | 脆弱性・バージョン監査 |
| mock-server | "モックサーバー", "APIモック" | OpenAPI からモックサーバー起動 |
| security-audit | "セキュリティ監査", "OWASP" | セキュリティ脆弱性の検出・監査 |
| oidc-scaffolding | "OIDC認証", "ソーシャルログイン", "Googleログイン" | OIDC 認証機能の自動生成 |

### 使用例

```bash
# 新機能追加
claude "ユーザー管理機能を追加してください"
→ module-scaffolding Skill が自動起動

# コードレビュー
claude "このPRをレビューしてください"
→ backend-code-review Skill が自動起動

# モックサーバー起動
claude "モックサーバーを起動して"
→ mock-server Skill が自動起動
```

## ローカル開発

### セットアップ

```bash
# 依存関係のセットアップ
./gradlew build

# Docker でデータベース起動
cd infra/docker && docker-compose up -d

# アプリケーション起動
./gradlew run
```

### モックサーバー起動

バックエンド実装前にクライアント開発を進める場合：

```bash
# モックサーバー起動（動的モード）
cd mock && docker-compose up -d

# 動作確認
curl http://localhost:4010/api/v1/users/me -H "Authorization: Bearer test"

# 停止
docker-compose down
```

| ポート | サービス |
|-------|---------|
| 4010 | 動的モックサーバー（ランダムデータ） |
| 4011 | 静的モックサーバー（example データ） |
| 8080 | 実サーバー（Ktor） |

### テスト実行

```bash
# 全テスト
./gradlew test

# 特定のテスト
./gradlew test --tests "*.UserUseCaseTest"
```

## API エンドポイント規約

### URL 設計

```
POST   /api/v1/auth/register     # ユーザー登録
POST   /api/v1/auth/login        # ログイン
GET    /api/v1/users/{id}        # ユーザー取得
PUT    /api/v1/users/{id}        # ユーザー更新
DELETE /api/v1/users/{id}        # ユーザー削除
GET    /api/v1/users             # ユーザー一覧
```

### レスポンス形式

**成功時:**
```json
{
  "data": { ... },
  "meta": {
    "timestamp": "2024-01-01T00:00:00Z"
  }
}
```

**エラー時:**
```json
{
  "error": {
    "code": "USER_NOT_FOUND",
    "message": "指定されたユーザーが見つかりません",
    "details": { ... }
  }
}
```

## セキュリティガイドライン

詳細は `docs/guides/security.md` を参照。

### OWASP Top 10 対応

| ID | 脆弱性 | 対策 |
|----|--------|------|
| A01 | Broken Access Control | 認証必須 + 所有者チェック + RBAC |
| A02 | Cryptographic Failures | BCrypt (コスト12+)、RS256 |
| A03 | Injection | Exposed パラメータバインディング |
| A05 | Security Misconfiguration | セキュリティヘッダー、CORS |
| A07 | Authentication Failures | レート制限、トークン有効期限 |
| A09 | Logging Failures | 監査ログ、機密情報マスク |

### 必須事項

- [ ] JWT トークン: アクセス15分、リフレッシュ7日
- [ ] パスワード: BCrypt（コスト係数 12 以上）
- [ ] SQL: Exposed パラメータバインディング
- [ ] HTTP: セキュリティヘッダー設定
- [ ] CORS: 許可オリジン明示指定
- [ ] Rate Limiting: 認証5回/分、API 60回/分
- [ ] 入力バリデーション: ValueObject 使用

### 禁止事項

- `.env`, `secrets.json` などの機密ファイルをコミットしない
- 認証情報をコードにハードコードしない
- ログに機密情報（パスワード、トークン）を出力しない
- 生 SQL クエリの使用
- CORS でワイルドカード（`*`）の使用（本番）

## 禁止事項

### Git 操作

- 破壊的な Git 操作（force push, hard reset）は実行前に確認を求める
- コミット時は必ず Co-Authored-By を付与する

### ビルド成果物

- `build/`, `.gradle/` は編集しない
- ビルド成果物は `.gitignore` で除外されている

## トラブルシューティング

### ビルドエラー

1. `./gradlew clean build --refresh-dependencies` を実行
2. Kotlin バージョンの競合がないか確認
3. `gradle.properties` の設定を確認

### データベース接続エラー

1. Docker コンテナが起動しているか確認
2. 環境変数 `DATABASE_URL` が正しいか確認
3. PostgreSQL のログを確認

### テスト失敗

1. テスト用データベースが起動しているか確認
2. マイグレーションが適用されているか確認
3. テストデータの前提条件を確認

## インフラストラクチャ

インフラストラクチャ設定は `infra/` ディレクトリで管理しています。詳細は `infra/README.md` を参照してください。

### Docker

```bash
# ローカル開発環境起動
cd infra/docker && docker-compose up -d

# 動作確認
curl http://localhost:8080/health

# ログ確認
docker-compose logs -f app

# 停止
docker-compose down
```

### Dockerイメージビルド

```bash
# ビルドスクリプト使用
./infra/scripts/build.sh

# 直接ビルド
docker build -f infra/docker/Dockerfile -t appmaster-backend:dev .
```

### AWS デプロイ

```bash
# 1. Terraform初期設定
cd infra/terraform/environments/dev
cp terraform.tfvars.example terraform.tfvars
# terraform.tfvars を編集
terraform init
terraform plan
terraform apply

# 2. デプロイスクリプト使用
./infra/scripts/deploy.sh dev
```

### 環境比較

| 項目 | Dev | Staging | Prod |
|------|-----|---------|------|
| ECS タスク数 | 1 | 2 | 3+ |
| RDS インスタンス | t4g.micro | t4g.small | t4g.medium |
| RDS Multi-AZ | No | No | Yes |
| オートスケール | No | Yes | Yes |
| HTTPS | Optional | Recommended | Required |

## インフラスキル

インフラ構築を対話的にガイドするスキルが `.claude/skills/` に用意されています。

```
Phase 1: backend-local-dev-setup     → ローカル開発環境セットアップ
Phase 2: backend-aws-bootstrap       → AWS 初期設定（IAM/S3/ECR/Secrets/OIDC）
Phase 3: backend-infra-deploy        → Terraform デプロイウィザード
Phase 4: backend-infra-monitor       → モニタリング・アラート・コスト監視
Phase 5: backend-infra-operations    → 運用ランブック
```

全フェーズをナビゲートするマスタースキル: `backend-infra-orchestrator`

GCP 代替: `backend-gcp-bootstrap`（将来対応）

## 参考リンク

- [Ktor Documentation](https://ktor.io/docs/)
- [Exposed Wiki](https://github.com/JetBrains/Exposed/wiki)
- [Koin Documentation](https://insert-koin.io/docs/quickstart/kotlin)
- [kotlinx.serialization Guide](https://github.com/Kotlin/kotlinx.serialization/blob/master/docs/serialization-guide.md)
- [Terraform AWS Provider](https://registry.terraform.io/providers/hashicorp/aws/latest/docs)
- [AWS ECS Best Practices](https://docs.aws.amazon.com/AmazonECS/latest/bestpracticesguide/intro.html)
