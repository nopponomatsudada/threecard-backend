# OIDC Scaffolding Skill

OpenID Connect (OIDC) 認証を実装するスキルです。Google、Apple などの外部プロバイダーによるソーシャルログインを追加します。

## トリガー

以下のキーワードで自動起動:

- "OIDC認証"
- "ソーシャルログイン"
- "Googleログイン"
- "Appleログイン"
- "外部認証"
- "SSO"

## 概要

このスキルは、以下のファイルを生成します:

```
domain/
├── model/
│   ├── OIDCProvider.kt
│   ├── OIDCAccount.kt
│   ├── OIDCState.kt
│   └── OIDCTokens.kt
└── repository/
    └── OIDCRepository.kt

data/
├── database/
│   ├── OIDCAccountsTable.kt
│   └── OIDCStatesTable.kt
├── oidc/
│   ├── OIDCClient.kt
│   ├── GoogleOIDCClient.kt
│   └── AppleOIDCClient.kt
└── repository/
    └── OIDCRepositoryImpl.kt

domain/usecase/oidc/
├── InitiateOIDCUseCase.kt
├── OIDCCallbackUseCase.kt
├── ExchangeProviderTokenUseCase.kt
├── LinkOIDCProviderUseCase.kt
├── UnlinkOIDCProviderUseCase.kt
└── GetLinkedOIDCAccountsUseCase.kt

routes/
├── dto/
│   └── OIDCDto.kt
└── OIDCRoutes.kt

di/
└── OIDCModule.kt
```

## 前提条件

以下が既に存在すること:

- [ ] `domain/model/User.kt` - ユーザーエンティティ
- [ ] `domain/repository/UserRepository.kt` - ユーザーリポジトリ
- [ ] `domain/repository/AuthRepository.kt` - 認証リポジトリ（JWT 発行）
- [ ] `domain/error/DomainError.kt` - ドメインエラー
- [ ] `data/database/UsersTable.kt` - ユーザーテーブル

## 使用方法

### 1. スキル呼び出し

```
claude "OIDC認証を追加してください"
```

### 2. プロバイダー選択

スキルは以下のプロバイダーをサポート:

| プロバイダー | 優先度 | 特記事項 |
|------------|-------|---------|
| Google | 高 | 標準的な OIDC |
| Apple | 高 | iOS アプリで必須の場合あり |
| GitHub | 低 | OAuth 2.0（ID Token なし） |
| LINE | 低 | 日本市場向け |

### 3. ファイル生成

各レイヤーのファイルを順番に生成します。

### 4. 設定ファイル更新

`application.conf` に OIDC 設定を追加します。

### 5. DI モジュール統合

`Application.kt` に OIDCModule を追加します。

## 生成されるコード

### Domain 層

- `OIDCProvider`: プロバイダー enum（GOOGLE, APPLE, etc.）
- `OIDCAccount`: OIDC リンク情報エンティティ
- `OIDCState`: CSRF/PKCE 用の認証状態
- `OIDCRepository`: OIDC リポジトリインターフェース

### Data 層

- `OIDCAccountsTable`: OIDC アカウントテーブル
- `OIDCStatesTable`: 認証状態テーブル
- `GoogleOIDCClient`: Google OIDC クライアント
- `AppleOIDCClient`: Apple OIDC クライアント
- `OIDCRepositoryImpl`: リポジトリ実装

### UseCase 層

- `InitiateOIDCUseCase`: 認証 URL 生成
- `OIDCCallbackUseCase`: コールバック処理（自動リンク対応）
- `ExchangeProviderTokenUseCase`: ネイティブ SDK トークン交換
- `LinkOIDCProviderUseCase`: 既存アカウントにリンク
- `UnlinkOIDCProviderUseCase`: リンク解除
- `GetLinkedOIDCAccountsUseCase`: リンク済み一覧取得

### Routes 層

- `OIDCDto`: リクエスト/レスポンス DTO
- `OIDCRoutes`: OIDC エンドポイント

### DI

- `OIDCModule.kt`: Koin モジュール

## API エンドポイント

| メソッド | パス | 認証 | 説明 |
|---------|------|------|------|
| POST | `/auth/oidc/{provider}/init` | 不要 | 認証 URL 取得 |
| POST | `/auth/oidc/{provider}/callback` | 不要 | コールバック処理 |
| POST | `/auth/oidc/{provider}/token` | 不要 | ネイティブトークン交換 |
| POST | `/auth/oidc/{provider}/link` | 必要 | アカウントリンク |
| DELETE | `/auth/oidc/{provider}` | 必要 | リンク解除 |
| GET | `/auth/oidc/accounts` | 必要 | リンク済み一覧 |

## セキュリティ対策

| 対策 | 説明 |
|-----|------|
| State パラメータ | CSRF 攻撃防止（32 バイト、1 回使用） |
| PKCE | 認可コード傍受攻撃防止（S256） |
| ID Token 署名検証 | JWKS による公開鍵検証 |
| Nonce 検証 | リプレイ攻撃防止 |
| State 有効期限 | 10 分で期限切れ |

## 依存関係

以下を `build.gradle.kts` に追加:

```kotlin
dependencies {
    // Ktor Client
    implementation("io.ktor:ktor-client-core:$ktor_version")
    implementation("io.ktor:ktor-client-cio:$ktor_version")
    implementation("io.ktor:ktor-client-content-negotiation:$ktor_version")
    implementation("io.ktor:ktor-client-logging:$ktor_version")

    // JWKS (ID Token 検証)
    implementation("com.auth0:jwks-rsa:0.22.1")
    implementation("com.auth0:java-jwt:4.4.0")
}
```

## application.conf 設定

```hocon
oidc {
    stateExpiryMinutes = 10

    google {
        enabled = true
        enabled = ${?OIDC_GOOGLE_ENABLED}
        clientId = ${?GOOGLE_CLIENT_ID}
        clientSecret = ${?GOOGLE_CLIENT_SECRET}
        iosClientId = ${?GOOGLE_IOS_CLIENT_ID}
        androidClientId = ${?GOOGLE_ANDROID_CLIENT_ID}
    }

    apple {
        enabled = true
        enabled = ${?OIDC_APPLE_ENABLED}
        clientId = ${?APPLE_CLIENT_ID}
        teamId = ${?APPLE_TEAM_ID}
        keyId = ${?APPLE_KEY_ID}
        privateKey = ${?APPLE_PRIVATE_KEY}
    }
}
```

## 既存コードへの変更

| ファイル | 変更内容 |
|---------|---------|
| `User.kt` | `passwordHash` を nullable に変更 |
| `UsersTable.kt` | `password_hash` を nullable に変更 |
| `DatabaseFactory.kt` | OIDC テーブル追加 |
| `DomainError.kt` | OIDC エラー追加 |
| `Routing.kt` | `oidcRoutes()` 追加 |

## 関連ドキュメント

- [OIDC 認証ガイド](../../../docs/guides/oidc-authentication.md)
- [認証ガイド](../../../docs/guides/authentication.md)
- [セキュリティガイド](../../../docs/guides/security.md)

## リファレンス

詳細な実装パターンは `reference/` ディレクトリを参照:

- [domain-layer.md](reference/domain-layer.md) - Domain 層の実装
- [data-layer.md](reference/data-layer.md) - Data 層の実装
- [usecase-layer.md](reference/usecase-layer.md) - UseCase 層の実装
- [routes-layer.md](reference/routes-layer.md) - Routes 層の実装
- [di-config.md](reference/di-config.md) - DI 設定
- [provider-setup.md](reference/provider-setup.md) - プロバイダー別設定
