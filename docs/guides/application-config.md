# アプリケーション設定ガイド

## 設定形式の選択

Ktor 3.x では **HOCON** (`.conf`) と **YAML** (`.yaml`) の2つの設定形式をサポートしています。

### HOCON 形式 (application.conf)

従来の形式。環境変数の上書き構文 `${?ENV_VAR}` が使用可能。

### YAML 形式 (application.yaml)

Ktor 3.x 推奨。ただし以下の点に注意:

1. **必須依存**: `ktor-server-config-yaml` を `build.gradle.kts` に追加すること
   ```kotlin
   implementation(libs.ktor.server.config.yaml)
   ```
   この依存がないと `EngineMain` が YAML 設定を読み込めず、「Neither port nor sslPort specified」エラーが発生する。

2. **環境変数の扱い**: HOCON の `${?ENV_VAR}` 構文は **YAML では使用不可**
   ```yaml
   # ❌ YAML では動作しない（HOCON 専用構文）
   jwt:
     secret: "${?JWT_SECRET}"

   # ✅ 開発用のデフォルト値を直接記述
   jwt:
     secret: "dev-secret-change-in-production"
   ```

   環境変数で上書きしたい場合は、コード内で `System.getenv()` を使用:
   ```kotlin
   val secret = environment.config.propertyOrNull("jwt.secret")?.getString()
       ?: System.getenv("JWT_SECRET")
       ?: "dev-secret-change-in-production"
   ```

## 基本構成（HOCON 形式）

`src/main/resources/application.conf` に HOCON 形式で設定を記述します。

### 完全なテンプレート

```hocon
ktor {
    deployment {
        port = 8080
        port = ${?PORT}

        # 開発環境でのホットリロード
        watch = [ "classes", "resources" ]
    }

    application {
        modules = [ com.example.ApplicationKt.module ]
    }

    # 開発モード
    development = false
    development = ${?KTOR_DEVELOPMENT}
}

# JWT 認証設定
jwt {
    secret = "your-super-secret-jwt-key-change-in-production-min-32-chars"
    secret = ${?JWT_SECRET}
    issuer = "appmaster"
    issuer = ${?JWT_ISSUER}
    audience = "appmaster-users"
    audience = ${?JWT_AUDIENCE}
    realm = "AppMaster API"

    # トークン有効期限（分）
    accessTokenExpireMinutes = 15
    accessTokenExpireMinutes = ${?JWT_ACCESS_EXPIRE_MINUTES}

    # リフレッシュトークン有効期限（日）
    refreshTokenExpireDays = 30
    refreshTokenExpireDays = ${?JWT_REFRESH_EXPIRE_DAYS}
}

# データベース設定
database {
    # 開発用（H2 インメモリ）
    driver = "org.h2.Driver"
    driver = ${?DATABASE_DRIVER}

    url = "jdbc:h2:mem:appmaster;DB_CLOSE_DELAY=-1"
    url = ${?DATABASE_URL}

    user = "sa"
    user = ${?DATABASE_USER}

    password = ""
    password = ${?DATABASE_PASSWORD}

    # コネクションプール設定
    maxPoolSize = 10
    maxPoolSize = ${?DATABASE_MAX_POOL_SIZE}
}

# CORS 設定
cors {
    # 許可するオリジン（カンマ区切り）
    allowedOrigins = "http://localhost:3000,http://localhost:5173"
    allowedOrigins = ${?CORS_ALLOWED_ORIGINS}

    # 認証ヘッダーを許可
    allowCredentials = true
}

# レート制限
rateLimit {
    # 認証エンドポイント（1分あたり）
    authRequestsPerMinute = 5
    authRequestsPerMinute = ${?RATE_LIMIT_AUTH}

    # API エンドポイント（1分あたり）
    apiRequestsPerMinute = 60
    apiRequestsPerMinute = ${?RATE_LIMIT_API}
}

# ロギング設定
logging {
    level = "INFO"
    level = ${?LOG_LEVEL}
}
```

## 環境別設定

### 開発環境 (.env.development)

```bash
KTOR_DEVELOPMENT=true
PORT=8080

# H2 インメモリDB（開発用）
DATABASE_DRIVER=org.h2.Driver
DATABASE_URL=jdbc:h2:mem:appmaster;DB_CLOSE_DELAY=-1
DATABASE_USER=sa
DATABASE_PASSWORD=

# JWT（開発用）
JWT_SECRET=dev-secret-key-change-this-in-production-32chars
JWT_ACCESS_EXPIRE_MINUTES=60
JWT_REFRESH_EXPIRE_DAYS=7

# CORS
CORS_ALLOWED_ORIGINS=http://localhost:3000,http://localhost:5173

LOG_LEVEL=DEBUG
```

### 本番環境 (.env.production)

```bash
KTOR_DEVELOPMENT=false
PORT=8080

# PostgreSQL
DATABASE_DRIVER=org.postgresql.Driver
DATABASE_URL=jdbc:postgresql://db.example.com:5432/appmaster
DATABASE_USER=appmaster_user
DATABASE_PASSWORD=${SECURE_DB_PASSWORD}
DATABASE_MAX_POOL_SIZE=20

# JWT（本番用 - 必ず変更）
JWT_SECRET=${SECURE_JWT_SECRET}
JWT_ACCESS_EXPIRE_MINUTES=15
JWT_REFRESH_EXPIRE_DAYS=30

# CORS（本番ドメインのみ）
CORS_ALLOWED_ORIGINS=https://app.example.com

# レート制限
RATE_LIMIT_AUTH=5
RATE_LIMIT_API=100

LOG_LEVEL=WARN
```

## 設定の読み取り方

### Application.kt での読み取り

```kotlin
fun Application.module() {
    val config = environment.config

    // JWT 設定
    val jwtSecret = config.property("jwt.secret").getString()
    val jwtIssuer = config.property("jwt.issuer").getString()
    val jwtAudience = config.property("jwt.audience").getString()

    // Database 設定
    val dbDriver = config.property("database.driver").getString()
    val dbUrl = config.property("database.url").getString()

    // オプション値（デフォルト付き）
    val accessTokenExpire = config.propertyOrNull("jwt.accessTokenExpireMinutes")
        ?.getString()?.toLongOrNull() ?: 15L
}
```

### Config クラスでのカプセル化

```kotlin
// config/AppConfig.kt
data class AppConfig(
    val jwt: JwtConfig,
    val database: DatabaseConfig,
    val cors: CorsConfig
) {
    companion object {
        fun from(config: ApplicationConfig): AppConfig = AppConfig(
            jwt = JwtConfig.from(config),
            database = DatabaseConfig.from(config),
            cors = CorsConfig.from(config)
        )
    }
}

data class JwtConfig(
    val secret: String,
    val issuer: String,
    val audience: String,
    val realm: String,
    val accessTokenExpireMinutes: Long,
    val refreshTokenExpireDays: Long
) {
    companion object {
        fun from(config: ApplicationConfig): JwtConfig = JwtConfig(
            secret = config.property("jwt.secret").getString(),
            issuer = config.property("jwt.issuer").getString(),
            audience = config.property("jwt.audience").getString(),
            realm = config.property("jwt.realm").getString(),
            accessTokenExpireMinutes = config.propertyOrNull("jwt.accessTokenExpireMinutes")
                ?.getString()?.toLongOrNull() ?: 15L,
            refreshTokenExpireDays = config.propertyOrNull("jwt.refreshTokenExpireDays")
                ?.getString()?.toLongOrNull() ?: 30L
        )
    }
}

data class DatabaseConfig(
    val driver: String,
    val url: String,
    val user: String,
    val password: String,
    val maxPoolSize: Int
) {
    companion object {
        fun from(config: ApplicationConfig): DatabaseConfig = DatabaseConfig(
            driver = config.property("database.driver").getString(),
            url = config.property("database.url").getString(),
            user = config.property("database.user").getString(),
            password = config.property("database.password").getString(),
            maxPoolSize = config.propertyOrNull("database.maxPoolSize")
                ?.getString()?.toIntOrNull() ?: 10
        )
    }
}

data class CorsConfig(
    val allowedOrigins: List<String>,
    val allowCredentials: Boolean
) {
    companion object {
        fun from(config: ApplicationConfig): CorsConfig = CorsConfig(
            allowedOrigins = config.propertyOrNull("cors.allowedOrigins")
                ?.getString()?.split(",")?.map { it.trim() } ?: emptyList(),
            allowCredentials = config.propertyOrNull("cors.allowCredentials")
                ?.getString()?.toBoolean() ?: false
        )
    }
}
```

## logback.xml

`src/main/resources/logback.xml`:

```xml
<configuration>
    <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} -- %msg%n</pattern>
        </encoder>
    </appender>

    <!-- Ktor ログ -->
    <logger name="io.ktor" level="INFO"/>

    <!-- Exposed SQL ログ（開発時は DEBUG） -->
    <logger name="Exposed" level="INFO"/>

    <!-- Netty ログ -->
    <logger name="io.netty" level="WARN"/>

    <!-- アプリケーションログ -->
    <logger name="com.example" level="DEBUG"/>

    <root level="INFO">
        <appender-ref ref="STDOUT"/>
    </root>
</configuration>
```

## セキュリティ注意事項

### 機密情報の管理

```bash
# ❌ 絶対にコミットしない
.env
.env.production
secrets.json
*.pem
*.key

# ✅ テンプレートのみコミット
.env.example
```

### .env.example

```bash
# コピーして .env を作成
# cp .env.example .env

PORT=8080
DATABASE_URL=jdbc:postgresql://localhost:5432/appmaster
DATABASE_USER=your_user
DATABASE_PASSWORD=your_password
JWT_SECRET=your-32-character-secret-key-here
```

### 本番環境での設定

本番環境では環境変数を使用:

```bash
# Docker Compose
environment:
  - DATABASE_URL=${DATABASE_URL}
  - JWT_SECRET=${JWT_SECRET}

# Kubernetes Secret
kubectl create secret generic app-secrets \
  --from-literal=jwt-secret='...' \
  --from-literal=db-password='...'
```
