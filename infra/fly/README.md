# Fly.io デプロイガイド

threecard-backend を [Fly.io](https://fly.io) にデプロイする手順。DB は [Supabase](../supabase/README.md) のマネージド PostgreSQL を利用する。

同じ構成は **bucketlist-backend (fly app: `bucketlist-api`)** で運用実績あり。このドキュメントはその構成に準拠している。

## 前提

- [`flyctl`](https://fly.io/docs/flyctl/install/) インストール済み
- Fly.io アカウント作成済み (`fly auth login`)
- Supabase プロジェクト作成済み → [`infra/supabase/README.md`](../supabase/README.md) 参照
- Docker イメージは `infra/docker/Dockerfile` を流用（Ktor fat JAR ビルド）

## 構成ファイル

| ファイル | 場所 | 役割 |
|---------|-----|------|
| `fly.toml` | **`threecard-backend/fly.toml`**（プロジェクトルート） | Fly.io アプリ設定。ビルドコンテキスト = このファイルのあるディレクトリ。ここを動かすと Dockerfile の `COPY build.gradle.kts` が壊れるので固定 |
| `Dockerfile` | `infra/docker/Dockerfile` | fat JAR マルチステージビルド |
| README | `infra/fly/README.md`（このファイル） | 手順書 |

## 初回セットアップ

### 1. アプリ作成

```bash
# threecard-backend/ に cd
cd threecard-backend

# アプリ名を予約（fly.toml の app = "threecard-backend" を使う）
fly apps create threecard-backend
```

アプリ名が既に使われている場合は `fly.toml` の `app` を別名（例: `threecard-api`）に変更してから再実行。

### 2. Secrets 設定

Supabase 側で取得した接続情報と JWT シークレットを Fly.io の暗号化 Secrets として登録する。

```bash
# threecard-backend/ で実行（fly.toml をカレントから自動検出）
fly secrets set \
  DATABASE_URL='jdbc:postgresql://aws-0-ap-northeast-1.pooler.supabase.com:5432/postgres?sslmode=require' \
  DATABASE_USER='postgres.<project_ref>' \
  DATABASE_PASSWORD='<db_password>' \
  JWT_SECRET="$(openssl rand -base64 48)"
```

> **NOTE**:
> - `DATABASE_URL` は JDBC URL 形式 (`jdbc:postgresql://...`)。Supabase Dashboard がコピペで渡す URL は `postgresql://` なので先頭に `jdbc:` を付ける。
> - `sslmode=require` を必ず付与（Supabase は TLS 必須）。
> - **Session Pooler (port 5432)** を使う。Transaction Pooler (6543) は Flyway の advisory lock が動かず失敗する。
> - `DATABASE_USER` は `postgres.<project_ref>` 形式（ドットあり）。
> - `JWT_SECRET` は 32 文字以上。これを変更すると既存 JWT が全て無効化される（AN-14 検証済みの refresh-first 復旧経路でクライアントは自動復旧する）。

参考: bucketlist-backend で実際に設定されている secrets（同じ顔ぶれ）:
```
DATABASE_URL, DATABASE_USER, DATABASE_PASSWORD, JWT_SECRET
```

### 3. CORS 許可ホスト（本番クライアント URL が確定したら）

アプリは `CORS_ALLOWED_HOSTS` を `application.yaml` 経由で読む。デフォルトのままでも OK だが、本番 Web クライアントを追加する場合:

```bash
fly secrets set CORS_ALLOWED_HOSTS='https://app.example.com'
```

## デプロイ

```bash
cd threecard-backend
fly deploy
```

- 初回デプロイ時に `infra/docker/Dockerfile` でビルド（fat JAR 生成）。
- アプリ起動時に **Flyway マイグレーション V1 → V2 が自動実行**される（`plugins/Database.kt` の `runMigrations()`）。
- ヘルスチェック `GET /health` が 200 を返すと公開される。

## 動作確認

```bash
# ステータス
fly status

# ライブログ
fly logs

# 公開 URL 経由で health check
curl https://threecard-backend.fly.dev/health
# → {"status":"UP"}

# デバイス認証（Android / iOS クライアントと同じ経路）
curl -X POST https://threecard-backend.fly.dev/api/v1/auth/device \
  -H 'Content-Type: application/json' \
  -d '{"deviceId":"00000000-0000-0000-0000-000000000001"}'
```

## クライアント側の BASE_URL 更新

デプロイ成功後、各プラットフォームの BASE_URL を本番 URL に差し替える:

- **Android**: `NetworkModule` / `BuildConfig.BASE_URL` → `https://threecard-backend.fly.dev/api/v1/`
- **iOS**: Network 設定 → 同上

## スケーリング

```bash
# マシン数固定（auto-stop を無効化）
fly scale count 1

# VM サイズ変更（メモリ不足時）
fly scale vm shared-cpu-1x --memory 1024

# リージョン追加
fly regions add hkg
```

## ロールバック

```bash
fly releases
fly releases rollback <version>
```

## トラブルシューティング

### `Database initialization failed`

- Supabase 側で DB が pause されていないか確認（Free tier は 7 日間アイドルで pause）
- `DATABASE_URL` が `jdbc:postgresql://` で始まり `sslmode=require` が付いているか
- Session Pooler (5432) を使っているか（Transaction Pooler 6543 は NG）

### `/health` が 503

- `fly logs` でスタックトレースを確認
- Flyway migration エラーなら Supabase SQL Editor で `select * from flyway_schema_history;` を確認

### JWT が再起動で無効化される

- `JWT_SECRET` が secrets に設定されているか（`fly secrets list`）。未設定だと起動時ランダム生成にフォールバック。

### OOMKilled / メモリ不足

- 512mb で不足する場合は `fly scale vm shared-cpu-1x --memory 1024` で増強。

## 参考

- 実運用中のサンプル: `../../../bucketlist-parent/bucketlist-backend/fly.toml`
- [Fly.io Kotlin/JVM guide](https://fly.io/docs/languages-and-frameworks/)
- [Supabase セットアップ](../supabase/README.md)
- [Docker Dockerfile](../docker/Dockerfile)
