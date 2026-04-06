# AppMaster Backend

AppMaster プロジェクトのバックエンド API サーバーです。

## 技術スタック

- **言語**: Kotlin
- **フレームワーク**: Ktor 3.x
- **認証**: JWT
- **ORM**: Exposed
- **データベース**: PostgreSQL
- **DI**: Koin
- **テスト**: Kotest + Ktor Test

## クイックスタート

### 前提条件

- JDK 17 以上
- Docker & Docker Compose

### セットアップ

```bash
# リポジトリをクローン
git clone git@github.com:nopponomatsudada/app-master-backend.git
cd app-master-backend

# データベース起動
docker-compose -f infra/docker-compose.yml up -d

# アプリケーション起動
./gradlew run
```

### API アクセス

```bash
# ヘルスチェック
curl http://localhost:8080/health

# ユーザー登録
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email": "user@example.com", "password": "password123"}'
```

## ドキュメント

- [アーキテクチャガイド](docs/guides/architecture-guidelines.md)
- [実装手順書](docs/module-scaffolding.md)
- [Claude Code ガイド](CLAUDE.md)

## ディレクトリ構成

```
src/main/kotlin/
├── Application.kt       # エントリーポイント
├── plugins/             # Ktor プラグイン
├── domain/              # ドメイン層
├── data/                # データ層
└── routes/              # API エンドポイント
```

## テスト

```bash
# 全テスト実行
./gradlew test

# カバレッジレポート生成
./gradlew jacocoTestReport
```

## ライセンス

Private - All Rights Reserved
