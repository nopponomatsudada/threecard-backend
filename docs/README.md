# appmaster-backend ドキュメント

バックエンド API サーバーのアーキテクチャガイドと実装手順書をまとめています。

## ディレクトリ構成

- `guides/` - レイヤ別アーキテクチャガイド
- `module-scaffolding.md` - 新規機能の実装手順

## 推奨読み順

1. `guides/architecture-guidelines.md` - 全体方針
2. `guides/domain-layer.md` - ドメイン層
3. `guides/data-layer.md` - データ層
4. `guides/routes-layer.md` - API エンドポイント
5. `guides/error-handling.md` - エラーハンドリング
6. `guides/testing-guidelines.md` - テスト戦略
7. `module-scaffolding.md` - 実装手順

## クロスプラットフォーム連携

バックエンド API は Android/iOS の両プラットフォームから呼び出されます。以下の点を統一してください:

- **API スキーマ**: OpenAPI 仕様で定義
- **エラーコード**: `appmaster-parent/docs/` で定義された共通エラーコードを使用
- **ドメイン語彙**: 各プラットフォームと同一の用語を使用
