# アーキテクチャガイド

このディレクトリには、バックエンド API のアーキテクチャに関するガイドが含まれています。

## ガイド一覧

### アーキテクチャ

| ファイル | 説明 |
|---------|------|
| [architecture-guidelines.md](architecture-guidelines.md) | 全体アーキテクチャ方針 |
| [domain-layer.md](domain-layer.md) | ドメイン層の設計ガイド |
| [data-layer.md](data-layer.md) | データ層の設計ガイド |
| [routes-layer.md](routes-layer.md) | API エンドポイント設計ガイド |
| [error-handling.md](error-handling.md) | エラーハンドリング方針 |

### 実装パターン

| ファイル | 説明 |
|---------|------|
| [authentication.md](authentication.md) | JWT + Refresh Token 認証ガイド |
| [pagination.md](pagination.md) | ページネーション実装パターン |
| [dependency-injection.md](dependency-injection.md) | Koin DI 設定ガイド |

### 設定・ビルド

| ファイル | 説明 |
|---------|------|
| [build-configuration.md](build-configuration.md) | Gradle ビルド設定 |
| [application-config.md](application-config.md) | application.conf 設定 |

### 品質・運用

| ファイル | 説明 |
|---------|------|
| [coding-standards.md](coding-standards.md) | コーディング規約 |
| [testing-guidelines.md](testing-guidelines.md) | テスト戦略 |
| [security.md](security.md) | セキュリティガイド（OWASP Top 10） |
| [troubleshooting.md](troubleshooting.md) | トラブルシューティング |
| [deployment.md](deployment.md) | デプロイメントガイド |

## 推奨読み順

### 初めての開発者向け

1. `architecture-guidelines.md` で全体像を把握
2. `build-configuration.md` でビルド設定を理解
3. `application-config.md` で設定ファイルを理解
4. `domain-layer.md` → `data-layer.md` → `routes-layer.md` の順でレイヤを理解
5. `dependency-injection.md` で DI 設定を理解

### 実装時

1. `coding-standards.md` でコーディング規約を確認
2. `pagination.md` でページネーション実装を確認
3. `error-handling.md` でエラー設計を確認
4. `testing-guidelines.md` でテスト方針を確認

### 問題発生時

1. `troubleshooting.md` でよくある問題を確認
2. `data-layer.md` の「よくある問題と解決法」セクション
3. `routes-layer.md` の「必須インポート」セクション

### デプロイ時

1. `security.md` でセキュリティチェックリストを確認
2. `deployment.md` でデプロイ手順を確認
