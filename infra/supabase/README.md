# Supabase セットアップガイド

threecard-backend のマネージド PostgreSQL として [Supabase](https://supabase.com) を利用する。Fly.io デプロイ（[`infra/fly/README.md`](../fly/README.md)）から接続する前提。

## 前提

- Supabase アカウント作成済み
- スキーマ管理はアプリ側の Flyway（`src/main/resources/db/migration/V1__*.sql`, `V2__*.sql`）が行う。Supabase Studio で DDL を書かない。
- 認証は自前 JWT。Supabase Auth / RLS は使わない（将来 OIDC を導入するなら検討）。

## セットアップ手順

### 1. プロジェクト作成

1. [Supabase Dashboard](https://supabase.com/dashboard) → **New Project**
2. 設定:
   - **Name**: `threecard-backend`
   - **Region**: `Northeast Asia (Tokyo) ap-northeast-1`（Fly.io `nrt` と同リージョン推奨）
   - **Database Password**: 強力なパスワードを生成（Secrets Manager / パスワードマネージャに保管）
   - **Plan**: Free で開始。無料枠は 500 MB DB / 2 プロジェクトまで、7 日間アイドルで pause されるので注意。

### 2. 接続文字列の取得

Dashboard → **Project Settings** → **Database** → **Connection string** タブ。

| モード | ポート | 用途 |
|-------|------|------|
| Direct connection | 5432 | IPv6 のみ。Fly.io は IPv6 対応だが Free tier では不可（2024-01 以降）|
| **Session pooler** | **5432** | **IPv4 対応。Flyway と長時間接続向け。本プロジェクトはこちらを使う** |
| Transaction pooler | 6543 | IPv4 対応だがステートレス。Flyway の advisory lock が動かないので **使わない** |

**Session pooler の URL 例**:

```
postgresql://postgres.<project_ref>:<db_password>@aws-0-ap-northeast-1.pooler.supabase.com:5432/postgres
```

これを JDBC 形式に変換して Fly.io の `DATABASE_URL` に設定する:

```
jdbc:postgresql://aws-0-ap-northeast-1.pooler.supabase.com:5432/postgres?user=postgres.<project_ref>&password=<db_password>&sslmode=require
```

### 3. スキーマ初期化

アプリ起動時に Flyway が自動で以下を実行する:

- `V1__initial_schema.sql` — BE-1..BE-7 で作成されたテーブル（users, themes, bests, best_items, collections, collection_cards, tags）
- `V2__device_secret_and_refresh_tokens.sql` — device_secret_hash カラム追加 / refresh_tokens / jwt_blocklist

Supabase Studio で事前に何かする必要はない。初回デプロイでマイグレーションを流し切ること。

### 4. 動作確認

```bash
# psql で直接接続（Session pooler）
psql 'postgresql://postgres.<project_ref>:<db_password>@aws-0-ap-northeast-1.pooler.supabase.com:5432/postgres?sslmode=require'

# テーブル一覧
\dt
# → users, themes, bests, best_items, collections, collection_cards,
#   device_secrets, refresh_tokens, jwt_blocklist, flyway_schema_history
```

## 運用

### バックアップ

- Free tier: 日次自動バックアップ（7日保持）、手動ダウンロード不可
- Pro tier: PITR（Point-in-Time Recovery）、ダウンロード可
- 無料プランでは重要データ投入前に手動でダンプを取ることを推奨:
  ```bash
  pg_dump 'postgresql://...?sslmode=require' > backup_$(date +%Y%m%d).sql
  ```

### 監視

- Dashboard → **Reports** → DB usage, API requests, Auth events を確認
- Fly.io 側のアプリログで SQL エラー / 接続エラーを監視

### スケーリング

無料枠（500MB, 2 vCPU shared）を超えたら Pro tier（$25/月）にアップグレード。Dashboard からワンクリック。

## ローカル開発との使い分け

| 環境 | DB |
|------|-----|
| ローカル開発 | `infra/docker/docker-compose.yml` の PostgreSQL コンテナ |
| テスト (JUnit) | H2 in-memory |
| 本番 (Fly.io) | **Supabase (this doc)** |

ローカル開発で Supabase に接続したい場合は `.env` に `DATABASE_URL` を設定して `./gradlew run` でも可だが、Free tier は接続数に制限があるためチーム開発では非推奨。

## セキュリティ注意事項

- **DB パスワードをコミットしない**。Fly.io Secrets と 1Password などに保管。
- Supabase Dashboard の Access Token もコミットしない。
- 将来 Row Level Security (RLS) を使う場合はアプリ側の JWT とは別に `service_role` / `anon` key の扱いを設計する。現時点では **使用しない**。
- `sslmode=require` を必ず付与（盗聴対策）。

## トラブルシューティング

### `FATAL: Tenant or user not found`

- Session pooler の URL で `user` は `postgres.<project_ref>` 形式（ドットあり）。`postgres` だけだと拒否される。

### `could not create advisory lock` (Flyway)

- Transaction pooler (6543) に繋いでいる可能性。Session pooler (5432) に切り替える。

### Free tier で DB が pause された

- 7 日間アクセスがないと自動 pause される。Dashboard から Resume するか、定期ヘルスチェック（cron で `/health` を叩く）で保つ。

## 参考

- [Supabase Docs — Connecting to your database](https://supabase.com/docs/guides/database/connecting-to-postgres)
- [Supabase Docs — Connection Pooler](https://supabase.com/docs/guides/database/connecting-to-postgres#connection-pooler)
- [Fly.io デプロイ](../fly/README.md)
