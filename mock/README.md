# Mock Server

OpenAPI 仕様から自動生成されるモックサーバーです。
バックエンド実装前でも、クライアント（Android/iOS）の開発を進められます。

## クイックスタート

```bash
# モックサーバー起動
cd mock
docker-compose up -d

# ログ確認
docker-compose logs -f

# 停止
docker-compose down
```

## エンドポイント

| 環境 | URL | 説明 |
|------|-----|------|
| モックサーバー（動的） | http://localhost:4010 | ランダムなモックデータを返す |
| モックサーバー（静的） | http://localhost:4011 | OpenAPI の example を返す |
| 実サーバー | http://localhost:8080 | 本番実装 |

## クライアントからの接続

### Android エミュレータ

```kotlin
// エミュレータから localhost にアクセスする場合
private const val MOCK_BASE_URL = "http://10.0.2.2:4010/"

// BuildConfig で切り替え
val baseUrl = if (BuildConfig.USE_MOCK) MOCK_BASE_URL else PROD_BASE_URL
```

### iOS シミュレータ

```swift
// シミュレータから localhost にアクセス
let mockBaseURL = URL(string: "http://localhost:4010")!

// 環境で切り替え
let baseURL = useMock ? mockBaseURL : prodBaseURL
```

## モックモード

### 動的モード（デフォルト）

```bash
docker-compose up mock-server
```

- OpenAPI スキーマに基づいてランダムなデータを生成
- 毎回異なるレスポンスが返る
- 様々なデータパターンのテストに有用

### 静的モード

```bash
docker-compose --profile static up mock-server-static
```

- OpenAPI の `example` フィールドの値を返す
- 常に同じレスポンスが返る
- 決まったデータでの動作確認に有用

## カスタムモックデータ

`data/` ディレクトリに JSON ファイルを配置することで、
特定のエンドポイントに対してカスタムレスポンスを返せます。

```
mock/
└── data/
    ├── users.json       # /api/v1/users のカスタムデータ
    └── auth.json        # /api/v1/auth/* のカスタムデータ
```

## API テスト

```bash
# ヘルスチェック
curl http://localhost:4010/api/v1/users/me \
  -H "Authorization: Bearer dummy-token"

# ログイン
curl -X POST http://localhost:4010/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email": "user@example.com", "password": "password123"}'

# ユーザー一覧
curl http://localhost:4010/api/v1/users \
  -H "Authorization: Bearer dummy-token"
```

## トラブルシューティング

### ポートが使用中

```bash
# 使用中のポートを確認
lsof -i :4010

# 別のポートで起動
MOCK_PORT=4020 docker-compose up
```

### コンテナが起動しない

```bash
# ログを確認
docker-compose logs mock-server

# OpenAPI 仕様の構文エラーを確認
docker run --rm -v $(pwd)/../api-spec:/api-spec stoplight/spectral lint /api-spec/openapi.yml
```

### Android エミュレータから接続できない

- `10.0.2.2` を使用しているか確認
- ファイアウォール設定を確認
- `adb reverse tcp:4010 tcp:4010` を試す

## 開発ワークフロー

```
1. OpenAPI 仕様を定義（api-spec/openapi.yml）
   ↓
2. モックサーバー起動（docker-compose up）
   ↓
3. Android/iOS でクライアント実装
   ↓
4. モックサーバーで動作確認
   ↓
5. バックエンド実装完了後、実サーバーに切り替え
```

## 関連ドキュメント

- [OpenAPI 仕様](../api-spec/openapi.yml)
- [API クライアント生成スキル](../.claude/skills/api-client-generator/SKILL.md)
- [モックサーバースキル](../.claude/skills/mock-server/SKILL.md)
