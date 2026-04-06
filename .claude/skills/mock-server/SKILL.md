---
name: mock-server
description: >
  OpenAPI 仕様からモックサーバーを起動し、クライアント開発を支援するスキル。
  バックエンド実装前でも Android/iOS の開発を進められる。
  トリガー: "モックサーバー", "mock server", "API モック", "ローカルサーバー"
---

# Mock Server Skill

OpenAPI 仕様から自動でモックサーバーを起動し、クライアント開発を支援します。

## クイックスタート

```bash
# モックサーバー起動
cd mock && docker-compose up -d

# 動作確認
curl http://localhost:4010/api/v1/users/me -H "Authorization: Bearer test"

# 停止
docker-compose down
```

## 使用シナリオ

### 1. 新規機能開発時

```
ユーザー: 「タスク管理機能を開発したい。モックサーバーを準備して」

AI:
1. api-spec/openapi.yml にタスク API を追加
2. example データを定義
3. モックサーバー再起動
4. Android/iOS で API クライアント生成
```

### 2. クライアント単独開発時

```
ユーザー: 「バックエンドはまだないが、Android アプリを先に作りたい」

AI:
1. モックサーバー起動を確認
2. Android の baseURL をモックサーバーに設定
3. API クライアントを生成
4. UI 実装を進める
```

### 3. テストデータ準備

```
ユーザー: 「特定のエラーケースをテストしたい」

AI:
1. mock/data/ にカスタムレスポンスを配置
2. エラーレスポンスの example を追加
3. クライアントでエラーハンドリングをテスト
```

## モックサーバー設定

### 起動オプション

| コマンド | 説明 |
|---------|------|
| `docker-compose up -d` | 動的モック（ランダムデータ） |
| `docker-compose --profile static up -d` | 静的モック（example データ） |

### ポート設定

| ポート | サービス |
|-------|---------|
| 4010 | 動的モックサーバー |
| 4011 | 静的モックサーバー |
| 8080 | 実サーバー（Ktor） |

## クライアント接続設定

### Android エミュレータ

```kotlin
// build.gradle.kts
android {
    buildTypes {
        debug {
            buildConfigField("String", "API_BASE_URL", "\"http://10.0.2.2:4010/\"")
        }
        release {
            buildConfigField("String", "API_BASE_URL", "\"https://api.example.com/\"")
        }
    }
}

// ApiClient.kt
companion object {
    val BASE_URL = BuildConfig.API_BASE_URL
}
```

### iOS シミュレータ

```swift
// Configuration.swift
enum Configuration {
    #if DEBUG
    static let apiBaseURL = URL(string: "http://localhost:4010")!
    #else
    static let apiBaseURL = URL(string: "https://api.example.com")!
    #endif
}

// APIClient.swift
let client = APIClient(baseURL: Configuration.apiBaseURL, ...)
```

## 新規エンドポイント追加

### Step 1: OpenAPI 仕様を更新

```yaml
# api-spec/openapi.yml
paths:
  /api/v1/tasks:
    get:
      tags: [tasks]
      operationId: listTasks
      summary: タスク一覧取得
      responses:
        '200':
          description: 成功
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/TaskListResponse'
              example:                    # ← 必須！
                data:
                  - id: "task-001"
                    title: "サンプルタスク"
                    status: "PENDING"
                    dueDate: "2024-02-01"

components:
  schemas:
    Task:
      type: object
      properties:
        id:
          type: string
        title:
          type: string
        status:
          type: string
          enum: [PENDING, IN_PROGRESS, COMPLETED]
        dueDate:
          type: string
          format: date
```

### Step 2: モックサーバー再起動

```bash
cd mock && docker-compose restart
```

### Step 3: 動作確認

```bash
curl http://localhost:4010/api/v1/tasks
```

## カスタムモックデータ

特定のシナリオ用にカスタムデータを用意できます。

### ファイル配置

```
mock/
└── data/
    ├── users/
    │   ├── list.json       # GET /api/v1/users
    │   └── detail.json     # GET /api/v1/users/{id}
    └── errors/
        └── not_found.json  # 404 レスポンス
```

### 例: users/list.json

```json
{
  "data": [
    {
      "id": "test-user-001",
      "email": "test1@example.com",
      "name": "テストユーザー1",
      "status": "ACTIVE"
    },
    {
      "id": "test-user-002",
      "email": "test2@example.com",
      "name": "テストユーザー2",
      "status": "PENDING"
    }
  ],
  "meta": {
    "timestamp": "2024-01-20T10:00:00Z"
  }
}
```

## エラーレスポンスのテスト

### OpenAPI で定義

```yaml
responses:
  '404':
    description: Not Found
    content:
      application/json:
        example:
          error:
            code: "USER_NOT_FOUND"
            message: "ユーザーが見つかりません"
```

### Prism でエラーを返す

```bash
# 特定のステータスコードを返す
curl http://localhost:4010/api/v1/users/invalid-id \
  -H "Prefer: code=404"
```

## トラブルシューティング

### モックサーバーが起動しない

```bash
# ログ確認
docker-compose logs mock-server

# OpenAPI 構文チェック
docker run --rm -v $(pwd)/../api-spec:/spec stoplight/spectral lint /spec/openapi.yml
```

### example が返らない

- `example` フィールドが正しく定義されているか確認
- 静的モードで起動しているか確認（`--profile static`）

### Android から接続できない

- `10.0.2.2` を使用しているか確認
- `adb reverse tcp:4010 tcp:4010` を試す

## Reference

- [Prism Documentation](https://stoplight.io/open-source/prism)
- [OpenAPI Specification](https://swagger.io/specification/)
