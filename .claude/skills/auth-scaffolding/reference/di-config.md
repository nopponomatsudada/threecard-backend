# Auth DI 設定リファレンス

認証に関する Koin DI 設定のテンプレートです。

## AuthModule.kt

```kotlin
package {{package}}.di

import {{package}}.data.repository.AuthRepositoryImpl
import {{package}}.domain.repository.AuthRepository
import {{package}}.domain.usecase.auth.*
import io.ktor.server.application.*
import org.koin.dsl.module

/**
 * 認証モジュール
 *
 * JWT 設定を application.conf から読み込み
 */
fun authModule(environment: ApplicationEnvironment) = module {
    // ===== Repository =====

    single<AuthRepository> {
        AuthRepositoryImpl(
            jwtSecret = environment.config.property("jwt.secret").getString(),
            jwtIssuer = environment.config.property("jwt.issuer").getString(),
            jwtAudience = environment.config.property("jwt.audience").getString(),
            accessTokenExpiryMinutes = environment.config
                .propertyOrNull("jwt.accessTokenExpiryMinutes")
                ?.getString()?.toLong() ?: 15,
            refreshTokenExpiryDays = environment.config
                .propertyOrNull("jwt.refreshTokenExpiryDays")
                ?.getString()?.toLong() ?: 30
        )
    }

    // ===== UseCases =====

    single { RegisterUseCase(get(), get()) }
    single { LoginUseCase(get(), get()) }
    single { RefreshTokenUseCase(get(), get()) }
    single { LogoutUseCase(get()) }
    single { GetCurrentUserUseCase(get()) }

    // オプション
    // single { ChangePasswordUseCase(get(), get()) }
}
```

## Application.kt への統合

```kotlin
package {{package}}

import {{package}}.data.database.DatabaseFactory
import {{package}}.di.authModule
import {{package}}.di.userModule
import {{package}}.plugins.*
import io.ktor.server.application.*
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    // データベース初期化
    DatabaseFactory.init(environment)

    // DI 設定
    install(Koin) {
        slf4jLogger()
        modules(
            authModule(environment),  // ← 追加
            userModule(),
            // ... 他のモジュール
        )
    }

    // プラグイン設定
    configureAuthentication()  // ← 追加
    configureSerialization()
    configureErrorHandling()
    configureRouting()
}
```

## application.conf

```hocon
ktor {
    deployment {
        port = 8080
        port = ${?PORT}
    }
    application {
        modules = [ {{package}}.ApplicationKt.module ]
    }
}

jwt {
    # 本番環境では必ず環境変数で上書き
    secret = "your-32-character-secret-key-here-change-in-production"
    secret = ${?JWT_SECRET}

    issuer = "appmaster"
    issuer = ${?JWT_ISSUER}

    audience = "appmaster-api"
    audience = ${?JWT_AUDIENCE}

    realm = "AppMaster API"

    # トークン有効期限
    accessTokenExpiryMinutes = 15
    refreshTokenExpiryDays = 30
}

database {
    driver = "org.h2.Driver"
    driver = ${?DB_DRIVER}
    url = "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1"
    url = ${?DB_URL}
    user = ""
    user = ${?DB_USER}
    password = ""
    password = ${?DB_PASSWORD}
}
```

## build.gradle.kts

```kotlin
val ktor_version: String by project
val exposed_version: String by project
val koin_version: String by project

dependencies {
    // ===== Ktor =====
    implementation("io.ktor:ktor-server-core:$ktor_version")
    implementation("io.ktor:ktor-server-netty:$ktor_version")
    implementation("io.ktor:ktor-server-content-negotiation:$ktor_version")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktor_version")
    implementation("io.ktor:ktor-server-status-pages:$ktor_version")

    // ===== 認証 =====
    implementation("io.ktor:ktor-server-auth:$ktor_version")
    implementation("io.ktor:ktor-server-auth-jwt:$ktor_version")
    implementation("com.auth0:java-jwt:4.4.0")
    implementation("at.favre.lib:bcrypt:0.10.2")

    // ===== レート制限 (オプション) =====
    implementation("io.ktor:ktor-server-rate-limit:$ktor_version")

    // ===== DI =====
    implementation("io.insert-koin:koin-ktor:$koin_version")
    implementation("io.insert-koin:koin-logger-slf4j:$koin_version")

    // ===== Database =====
    implementation("org.jetbrains.exposed:exposed-core:$exposed_version")
    implementation("org.jetbrains.exposed:exposed-dao:$exposed_version")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposed_version")
    implementation("org.jetbrains.exposed:exposed-java-time:$exposed_version")
    implementation("com.h2database:h2:2.2.224")

    // ===== Test =====
    testImplementation("io.ktor:ktor-server-tests:$ktor_version")
    testImplementation("io.insert-koin:koin-test:$koin_version")
}
```

## gradle.properties

```properties
ktor_version=3.0.3
exposed_version=0.46.0
koin_version=3.5.3
kotlin_version=1.9.22
```

## テスト用 DI 設定

```kotlin
package {{package}}.di

import {{package}}.data.repository.AuthRepositoryImpl
import {{package}}.domain.repository.AuthRepository
import {{package}}.domain.usecase.auth.*
import org.koin.dsl.module

/**
 * テスト用認証モジュール
 *
 * 固定値を使用
 */
fun testAuthModule() = module {
    single<AuthRepository> {
        AuthRepositoryImpl(
            jwtSecret = "test-secret-key-32-characters-long",
            jwtIssuer = "test-issuer",
            jwtAudience = "test-audience",
            accessTokenExpiryMinutes = 60,  // テスト用に長め
            refreshTokenExpiryDays = 1
        )
    }

    single { RegisterUseCase(get(), get()) }
    single { LoginUseCase(get(), get()) }
    single { RefreshTokenUseCase(get(), get()) }
    single { LogoutUseCase(get()) }
    single { GetCurrentUserUseCase(get()) }
}
```

## よくある問題

### 1. 循環依存

```kotlin
// ❌ 循環依存が発生する可能性
single { AuthRepositoryImpl(get(), get()) }  // UserRepository を注入

// ✅ 認証に必要な設定のみ注入
single { AuthRepositoryImpl(jwtSecret, jwtIssuer, jwtAudience) }
```

### 2. 環境変数が読めない

```kotlin
// ✅ propertyOrNull で安全に読み込み
val secret = environment.config.propertyOrNull("jwt.secret")?.getString()
    ?: throw IllegalStateException("JWT_SECRET not configured")
```

### 3. テスト時の DI 置き換え

```kotlin
// テストで特定のモジュールを置き換え
startKoin {
    modules(testAuthModule())  // 本番モジュールの代わりに使用
}
```
