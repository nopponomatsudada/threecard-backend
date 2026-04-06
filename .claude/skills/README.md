# Agent Skills

このディレクトリには、AIエージェント（Claude Code等）がプロジェクト固有のパターンを理解し、高品質なコードを自動生成するための Agent Skills が含まれています。

## 利用可能な Skills

### 1. module-scaffolding
**新規機能の自動実装**

- **トリガー**: "新しい機能を追加", "エンティティを作成", "API追加"
- **機能**:
  - Domain → Data → Routes の順に実装ガイド
  - Clean Architecture パターンの自動適用
  - テンプレートベースのコード生成

**詳細**: [module-scaffolding/SKILL.md](module-scaffolding/SKILL.md)

### 2. backend-code-review
**コード品質の自動レビュー**

- **トリガー**: "コードレビュー", "品質チェック", "このPRをレビュー"
- **機能**:
  - アーキテクチャガイドライン遵守チェック
  - Ktor ベストプラクティス検証
  - セキュリティ脆弱性検出

**詳細**: [backend-code-review/SKILL.md](backend-code-review/SKILL.md)

### 3. template-code-generation
**テンプレートベースのコード生成**

- **トリガー**: "Entityを生成", "UseCaseを作成", "テンプレートから生成"
- **機能**:
  - プレースホルダー自動置換
  - 複数ファイルの一括生成
  - プロジェクト標準パターンの適用

**詳細**: [template-code-generation/SKILL.md](template-code-generation/SKILL.md)

### 4. architecture-guide
**Clean Architecture リファレンス**

- **トリガー**: 自動ロード（機能実装時）、"アーキテクチャについて教えて"
- **機能**:
  - レイヤー責務の明確化
  - 依存関係ルールの説明
  - 実装例の提供

**詳細**: [architecture-guide/SKILL.md](architecture-guide/SKILL.md)

### 5. dependency-audit
**Gradle依存関係の監査**

- **トリガー**: "依存関係を確認", "脆弱性チェック"
- **機能**:
  - 既知の脆弱性検出
  - 陳腐化した依存関係の検出
  - ライセンス互換性チェック

**詳細**: [dependency-audit/SKILL.md](dependency-audit/SKILL.md)

### 6. mock-server
**OpenAPI ベースのモックサーバー**

- **トリガー**: "モックサーバー", "mock server", "API モック", "ローカルサーバー"
- **機能**:
  - OpenAPI 仕様からモックレスポンスを自動生成
  - バックエンド実装前のクライアント開発を支援
  - 動的/静的モードの切り替え

**詳細**: [mock-server/SKILL.md](mock-server/SKILL.md)

### 7. security-audit
**セキュリティ監査**

- **トリガー**: "セキュリティ監査", "脆弱性チェック", "security audit", "OWASP チェック"
- **機能**:
  - OWASP Top 10 に基づく脆弱性検出
  - 認証・認可の検証
  - 依存関係の脆弱性チェック
  - セキュリティ設定の監査

**詳細**: [security-audit/SKILL.md](security-audit/SKILL.md)

### 8. auth-scaffolding
**認証機能の自動生成**

- **トリガー**: "認証を追加", "ログイン機能", "JWT設定", "認証機能を実装"
- **機能**:
  - JWT + Refresh Token パターンの完全実装
  - BCrypt パスワードハッシュ化
  - Token Rotation によるセキュリティ強化
  - Register/Login/Refresh/Logout の UseCase 生成

**詳細**: [auth-scaffolding/SKILL.md](auth-scaffolding/SKILL.md)

### 9. oidc-scaffolding
**OIDC 認証の自動生成**

- **トリガー**: "OIDC認証", "ソーシャルログイン", "Googleログイン", "Appleログイン", "外部認証", "SSO"
- **機能**:
  - Google / Apple OIDC 認証の完全実装
  - PKCE による認可コード傍受攻撃防止
  - JWKS による ID Token 署名検証
  - ネイティブ SDK トークン交換対応
  - アカウントリンク / リンク解除機能
  - 同一メールアドレスでの自動リンク

**詳細**: [oidc-scaffolding/SKILL.md](oidc-scaffolding/SKILL.md)

## 使い方

Claude Code は自動的に `.claude/skills/` ディレクトリを認識し、関連するタスクで適切な Skill を起動します。

```bash
# 新機能追加
claude "タスク管理機能を追加してください"
→ module-scaffolding Skill が自動起動

# コードレビュー
claude "このコードをレビューしてください"
→ backend-code-review Skill が自動起動

# モックサーバー起動
claude "モックサーバーを起動して"
→ mock-server Skill が自動起動

# セキュリティ監査
claude "セキュリティ監査をしてください"
→ security-audit Skill が自動起動

# OIDC 認証追加
claude "Googleログインを追加してください"
→ oidc-scaffolding Skill が自動起動
```

## Skill 構成

```
<skill-name>/
├── SKILL.md              # メインドキュメント（AIが参照）
├── reference/            # 詳細リファレンス
├── checklists/           # チェックリスト
└── scripts/              # 自動化スクリプト
```

## 関連リソース

- [Architecture Guidelines](../../docs/guides/architecture-guidelines.md)
- [Security Guide](../../docs/guides/security.md)
- [Module Scaffolding Guide](../../docs/module-scaffolding.md)
- [Coding Standards](../../docs/guides/coding-standards.md)
