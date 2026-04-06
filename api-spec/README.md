# API Specification

このディレクトリには、バックエンド API の OpenAPI 仕様が含まれています。

## ファイル構成

```
api-spec/
├── openapi.yml     # メインの OpenAPI 3.0 仕様
└── README.md       # このファイル
```

## 用途

この OpenAPI 仕様は以下の用途で使用されます：

| 用途 | 説明 |
|------|------|
| **モックサーバー** | `mock/docker-compose up` で自動起動 |
| **API ドキュメント** | Swagger UI / Redoc で閲覧可能 |
| **クライアント生成** | Android/iOS の API クライアントを自動生成 |
| **バリデーション** | リクエスト/レスポンスの型チェック |

## 仕様の確認

### Swagger Editor（オンライン）

1. https://editor.swagger.io/ を開く
2. `openapi.yml` の内容をペースト

### Swagger UI（ローカル）

```bash
docker run -p 8081:8080 \
  -e SWAGGER_JSON=/api-spec/openapi.yml \
  -v $(pwd):/api-spec \
  swaggerapi/swagger-ui
```

http://localhost:8081 でアクセス

### Redoc（ローカル）

```bash
npx redoc-cli serve openapi.yml
```

## 仕様の更新

### 新しいエンドポイント追加時

1. `openapi.yml` の `paths` セクションにエンドポイントを追加
2. 必要なスキーマを `components/schemas` に追加
3. **必ず `example` を含める**（モックサーバーで使用）
4. モックサーバーを再起動して動作確認

### example の書き方

```yaml
paths:
  /api/v1/tasks:
    get:
      responses:
        '200':
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/TaskListResponse'
              example:                          # ← 必須
                data:
                  - id: "task-001"
                    title: "サンプルタスク"
                    status: "PENDING"
                meta:
                  timestamp: "2024-01-20T10:00:00Z"
```

## プラットフォーム間の同期

この仕様は以下のプロジェクトと同期してください：

- **app-master-parent**: `api-spec/openapi.yml`（共通仕様として管理する場合）
- **app-master-android**: API クライアント生成の参照元
- **app-master-ios**: API クライアント生成の参照元

### 同期コマンド

```bash
# parent から同期（parent を SSOT とする場合）
cp ../app-master-parent/api-spec/openapi.yml ./api-spec/

# parent へ同期（backend を SSOT とする場合）
cp ./api-spec/openapi.yml ../app-master-parent/api-spec/
```

## エラーコード一覧

| コード | HTTP Status | 説明 |
|--------|-------------|------|
| `VALIDATION_ERROR` | 400 | バリデーションエラー |
| `INVALID_EMAIL` | 400 | メールアドレス形式が不正 |
| `UNAUTHORIZED` | 401 | 認証が必要 |
| `INVALID_TOKEN` | 401 | トークンが無効 |
| `TOKEN_EXPIRED` | 401 | トークンの有効期限切れ |
| `FORBIDDEN` | 403 | アクセス権限がない |
| `USER_NOT_FOUND` | 404 | ユーザーが見つからない |
| `EMAIL_ALREADY_EXISTS` | 409 | メールアドレスが既に使用されている |
| `INTERNAL_ERROR` | 500 | サーバー内部エラー |

## バリデーション

```bash
# OpenAPI 仕様の構文チェック
npx @stoplight/spectral lint openapi.yml

# または Docker で
docker run --rm -v $(pwd):/api-spec stoplight/spectral lint /api-spec/openapi.yml
```
