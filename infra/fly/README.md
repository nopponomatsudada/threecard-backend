# Fly.io デプロイガイド (Cloudflare Tunnel 経由)

threecard-backend を [Fly.io](https://fly.io) にデプロイする手順。DB は [Supabase](../supabase/README.md) のマネージド PostgreSQL、公開面は **Cloudflare Tunnel** 経由のみ。

`threecard-backend.fly.dev` は公開していない。すべての public トラフィックは `api.threecard.app` を入口とし Cloudflare Tunnel 越しに到達する。Cloudflare Access が `/api/v1/admin/*` および `/api/v1/admin/audit-logs` を保護し、管理者の追加削除は Cloudflare Zero Trust ダッシュボードで完結する。

## 前提

- [`flyctl`](https://fly.io/docs/flyctl/install/) インストール済み
- Fly.io アカウント作成済み (`fly auth login`)
- Supabase プロジェクト作成済み → [`infra/supabase/README.md`](../supabase/README.md) 参照
- Cloudflare アカウントと `threecard.app` ドメインが Cloudflare 管理下
- Cloudflare Zero Trust (Free) が有効化済み

## 構成ファイル

| ファイル | 場所 | 役割 |
|---------|-----|------|
| `fly.toml` | **`threecard-backend/fly.toml`** | Fly.io アプリ設定。`[http_service]` 無し = 公開面ゼロ。`[[services]]` は内部 TCP のみ |
| `Dockerfile` | `infra/docker/Dockerfile` | fat JAR + cloudflared バイナリの同梱 |
| `entrypoint.sh` | `infra/docker/entrypoint.sh` | cloudflared と Ktor を並行起動 |

## 初回セットアップ

### 1. Cloudflare Tunnel の作成

Cloudflare Zero Trust ダッシュボード ([one.dash.cloudflare.com](https://one.dash.cloudflare.com/)) から:

1. **Networks → Tunnels → Create a tunnel** で `Cloudflared` を選択
2. Tunnel 名: `threecard-backend` (任意)
3. **Install and run a connector** ステップでトークンが表示される — 後の `fly secrets set CLOUDFLARED_TOKEN=...` で使うので控える。トークンは `eyJhIjoi...` で始まる長い base64 文字列
4. **Public Hostname** タブで以下を登録:
   - **Subdomain**: `api`
   - **Domain**: `threecard.app`
   - **Service Type**: `HTTP`
   - **URL**: `localhost:8080`
5. DNS は自動で `api.threecard.app` が `<tunnel-id>.cfargotunnel.com` に CNAME される

### 2. Cloudflare Access (admin SSO) の設定

Zero Trust ダッシュボードから:

1. **Access → Applications → Add an application** で **Self-hosted** を選択
2. Application Domain に **両方**を登録:
   - `cms.threecard.app` (CMS フロント)
   - `api.threecard.app` (バックエンド admin パス)

   ※同一 application に入れることで `CF_Authorization` cookie がドメイン横断で発行され、SPA の 401 ループが発生しない
3. **Path** で admin だけ保護:
   - `api.threecard.app/api/v1/admin/*` のみ Access policy を適用 (公開 API は通す)
   - `cms.threecard.app/*` は全体保護
4. Identity Provider に Google Workspace を追加し、policy で許可 Group/email を指定。これが管理者の単一の SSOT になる
5. Application 設定の `AUD` 値を控える (後の `CF_ACCESS_AUD_TAG` で使用)

参考:
- https://developers.cloudflare.com/cloudflare-one/identity/authorization-cookie/
- https://developers.cloudflare.com/cloudflare-one/access-controls/policies/app-paths/

### 3. Fly アプリ作成

```bash
cd threecard-backend
fly apps create threecard-backend
```

アプリ名が既に使われている場合は `fly.toml` の `app` を別名に変更してから再実行。

### 4. Secrets 設定

```bash
# threecard-backend/ で実行
fly secrets set \
  DATABASE_URL='jdbc:postgresql://aws-0-ap-northeast-1.pooler.supabase.com:5432/postgres?sslmode=require' \
  DATABASE_USER='postgres.<project_ref>' \
  DATABASE_PASSWORD='<db_password>' \
  JWT_SECRET="$(openssl rand -base64 48)" \
  CLOUDFLARED_TOKEN='<step 1 で控えた tunnel token>' \
  CF_ACCESS_TEAM_DOMAIN='<your-team>.cloudflareaccess.com' \
  CF_ACCESS_AUD_TAG='<step 2 で控えた AUD>'
```

> **NOTE**:
> - `DATABASE_URL` は JDBC URL 形式 (`jdbc:postgresql://...`)。先頭に `jdbc:` を必ず付ける
> - `sslmode=require` を必ず付与 (Supabase は TLS 必須)
> - **Session Pooler (port 5432)** を使う。Transaction Pooler (6543) は Flyway の advisory lock が動かず失敗する
> - `JWT_SECRET` は端末認証用。32 文字以上必須
> - `CLOUDFLARED_TOKEN` は Tunnel 単位なので変更時は Cloudflare 側で再発行 → 再 set
> - `CF_ACCESS_TEAM_DOMAIN` / `CF_ACCESS_AUD_TAG` 未設定だと `cf-access` realm が全リクエスト 401 にする (admin API が全停止)

## デプロイ

```bash
cd threecard-backend
fly deploy
```

- 初回デプロイ時に `infra/docker/Dockerfile` でビルド (fat JAR + cloudflared 同梱)
- アプリ起動時に **Flyway マイグレーションが自動実行**される
- entrypoint が `cloudflared tunnel run` と Ktor を並行起動。両方の listener が立ち上がると Cloudflare Tunnel が ESTABLISHED になる
- Fly の TCP ヘルスチェックは内部 `localhost:8080` に対して走る

## 動作確認

```bash
# Fly のステータス
fly status

# ライブログ — cloudflared / Ktor 双方の出力が混じる
fly logs

# 直叩きできないことを確認 (期待: connection refused / timeout)
curl -v https://threecard-backend.fly.dev/health || true

# Tunnel 経由で health 200
curl -v https://api.threecard.app/health
# → {"status":"UP"}

# モバイルクライアントと同じ経路で device 認証
curl -X POST https://api.threecard.app/api/v1/auth/device \
  -H 'Content-Type: application/json' \
  -d '{"deviceId":"00000000-0000-0000-0000-000000000001"}'

# admin API は cf-access ログイン経由でのみ到達できる
# (curl だと 302 → cloudflareaccess.com にリダイレクト)
curl -v https://api.threecard.app/api/v1/admin/me
```

ブラウザでの動作確認:
1. `https://cms.threecard.app` を開く
2. Google SSO でログイン
3. cf-access cookie が両ドメインで発行され、CMS から admin API が叩ける

## クライアント側の BASE_URL

すでに本番設定済みの想定:

- **Android / iOS**: `https://api.threecard.app/api/v1/`
- **CMS**: `VITE_API_BASE_URL=https://api.threecard.app` (default fallback も同じ)

## スケーリング

```bash
# マシン数固定 (auto-stop は fly.toml で無効化済み)
fly scale count 2

# VM サイズ変更 (cloudflared + JVM の同居で 1024mb がベース)
fly scale vm shared-cpu-1x --memory 2048

# リージョン追加
fly regions add hkg
```

## ロールバック

```bash
fly releases
fly releases rollback <version>
```

緊急時に Tunnel を一時バイパスして fly.dev 直公開に戻す手順:
1. `fly.toml` の `[[services]]` を `[http_service]` に書き戻し (旧バージョンを `git show <commit>:fly.toml` から復元)
2. `fly deploy` で適用
3. Cloudflare DNS の `api.threecard.app` を Tunnel CNAME から `threecard-backend.fly.dev` の CNAME に向け直す
4. **戻し後は admin API が裸で公開される**ので、対応が終わったら速やかに Tunnel に戻す

## トラブルシューティング

### `Tunnel connector registered` のログが出ない
- `CLOUDFLARED_TOKEN` が secrets に入っているか (`fly secrets list`)
- Cloudflare Zero Trust ダッシュボードの Tunnel が `Healthy` 表示か
- Public Hostname の URL が `localhost:8080` (http) になっているか — `https` だと TLS handshake で失敗

### `https://api.threecard.app/health` が 502
- cloudflared は起動しているが Ktor が listener を握っていない
- `fly logs` で Ktor 側のスタックトレースを確認 (DB 接続失敗が多い)

### `https://api.threecard.app/api/v1/admin/me` が 200 を返さない
- `CF_ACCESS_TEAM_DOMAIN` / `CF_ACCESS_AUD_TAG` が secrets に入っているか
- Cloudflare Access policy のメール / Group に自分が含まれているか
- backend ログに `CF Access disabled` 警告が出ていないか

### `https://threecard-backend.fly.dev/health` が応答する
- `fly.toml` に `[http_service]` が残っている可能性。`grep -n http_service fly.toml` で確認
- 過去のデプロイでサービスが残っているなら `fly deploy` を再実行

### Database initialization failed
- Supabase 側で DB が pause されていないか確認 (Free tier は 7 日間アイドルで pause)
- `DATABASE_URL` が `jdbc:postgresql://` で始まり `sslmode=require` が付いているか
- Session Pooler (5432) を使っているか (Transaction Pooler 6543 は NG)

### JWT が再起動で無効化される
- `JWT_SECRET` が secrets に設定されているか (`fly secrets list`)。未設定だと起動時ランダム生成にフォールバック

### OOMKilled / メモリ不足
- 1024mb で不足する場合は `fly scale vm shared-cpu-1x --memory 2048` で増強

## 参考

- 実運用中のサンプル: `../../../bucketlist-parent/bucketlist-backend/fly.toml`
- [Fly.io Kotlin/JVM guide](https://fly.io/docs/languages-and-frameworks/)
- [Cloudflare Tunnel docs](https://developers.cloudflare.com/cloudflare-one/connections/connect-networks/)
- [Cloudflare Access docs](https://developers.cloudflare.com/cloudflare-one/applications/)
- [Supabase セットアップ](../supabase/README.md)
