# Auth Scaffolding Skill

JWT + Refresh Token パターンによる認証システムを生成するスキルです。

## トリガー

以下のキーワードで自動起動:

- "認証を追加"
- "ログイン機能"
- "JWT設定"
- "認証機能を実装"
- "auth"

## 概要

このスキルは、以下のファイルを生成します:

```
domain/
├── model/
│   └── AuthTokens.kt
└── repository/
    └── AuthRepository.kt

data/
├── database/
│   └── RefreshTokensTable.kt
└── repository/
    └── AuthRepositoryImpl.kt

domain/usecase/auth/
├── RegisterUseCase.kt
├── LoginUseCase.kt
├── RefreshTokenUseCase.kt
└── LogoutUseCase.kt

routes/
├── dto/
│   └── AuthDto.kt
└── AuthRoutes.kt

plugins/
└── Authentication.kt

di/
└── AuthModule.kt
```

## 前提条件

以下が既に存在すること:

- [ ] `domain/model/User.kt` - ユーザーエンティティ
- [ ] `domain/model/Email.kt` - Email 値オブジェクト
- [ ] `domain/model/Username.kt` - Username 値オブジェクト
- [ ] `domain/repository/UserRepository.kt` - ユーザーリポジトリ
- [ ] `domain/error/DomainError.kt` - ドメインエラー
- [ ] `data/database/UsersTable.kt` - ユーザーテーブル

## 使用方法

### 1. スキル呼び出し

```
claude "認証機能を追加してください"
```

### 2. パッケージ名の確認

スキルはプロジェクトのパッケージ名を自動検出します。
検出できない場合は確認を求めます。

### 3. ファイル生成

各レイヤーのファイルを順番に生成します。

### 4. 設定ファイル更新

`application.conf` に JWT 設定を追加します。

### 5. DI モジュール統合

`Application.kt` に AuthModule を追加します。

## 生成されるコード

### Domain 層

- `AuthTokens`: Access Token と Refresh Token のデータクラス
- `AuthRepository`: 認証リポジトリインターフェース

### Data 層

- `RefreshTokensTable`: Refresh Token テーブル定義
- `AuthRepositoryImpl`: 認証リポジトリ実装
  - JWT 生成 (java-jwt)
  - BCrypt パスワードハッシュ化
  - Token Rotation

### UseCase 層

- `RegisterUseCase`: ユーザー登録
- `LoginUseCase`: ログイン
- `RefreshTokenUseCase`: トークン更新
- `LogoutUseCase`: ログアウト

### Routes 層

- `AuthDto`: リクエスト/レスポンス DTO
- `AuthRoutes`: 認証エンドポイント

### Plugin

- `Authentication.kt`: Ktor JWT 認証設定

### DI

- `AuthModule.kt`: Koin モジュール

## セキュリティ設定

| 項目 | デフォルト値 |
|-----|------------|
| パスワードハッシュ | BCrypt (コスト 12) |
| Access Token 有効期限 | 15分 |
| Refresh Token 有効期限 | 30日 |
| JWT 署名アルゴリズム | HS256 |

## 依存関係

以下を `build.gradle.kts` に追加:

```kotlin
dependencies {
    // JWT
    implementation("com.auth0:java-jwt:4.4.0")

    // パスワードハッシュ化
    implementation("at.favre.lib:bcrypt:0.10.2")

    // Ktor 認証
    implementation("io.ktor:ktor-server-auth:$ktor_version")
    implementation("io.ktor:ktor-server-auth-jwt:$ktor_version")
}
```

## application.conf 設定

```hocon
jwt {
    secret = "your-32-character-secret-key-here"
    secret = ${?JWT_SECRET}
    issuer = "appmaster"
    issuer = ${?JWT_ISSUER}
    audience = "appmaster-api"
    audience = ${?JWT_AUDIENCE}
    realm = "AppMaster API"
    accessTokenExpiryMinutes = 15
    refreshTokenExpiryDays = 30
}
```

## 関連ドキュメント

- [認証ガイド](../../../docs/guides/authentication.md)
- [セキュリティガイド](../../../docs/guides/security.md)

## リファレンス

詳細な実装パターンは `reference/` ディレクトリを参照:

- [domain-layer.md](reference/domain-layer.md) - Domain 層の実装
- [data-layer.md](reference/data-layer.md) - Data 層の実装
- [usecase-layer.md](reference/usecase-layer.md) - UseCase 層の実装
- [routes-layer.md](reference/routes-layer.md) - Routes 層の実装
- [di-config.md](reference/di-config.md) - DI 設定
