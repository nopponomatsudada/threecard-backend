# 依存性注入（DI）ガイド

## 概要

Koin を使用した依存性注入の設定方法とベストプラクティスを説明します。

## 基本構成

### DI プラグイン設定

```kotlin
// plugins/DI.kt
package com.example.plugins

import com.example.di.*
import io.ktor.server.application.*
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger

fun Application.configureDI() {
    install(Koin) {
        slf4jLogger()
        modules(
            configModule(environment),
            repositoryModule,
            useCaseModule
        )
    }
}
```

### Application.kt での呼び出し

```kotlin
fun Application.module() {
    configureDI()      // 最初に DI を設定
    configureSecurity()
    configureSerialization()
    configureDatabase()
    configureRouting()
}
```

## モジュール構成

### 1. Config Module

設定値を DI コンテナに登録:

```kotlin
// di/ConfigModule.kt
package com.example.di

import com.example.config.*
import io.ktor.server.application.*
import org.koin.dsl.module

fun configModule(environment: ApplicationEnvironment) = module {
    single { AppConfig.from(environment.config) }
    single { get<AppConfig>().jwt }
    single { get<AppConfig>().database }
    single { get<AppConfig>().cors }
}
```

### 2. Repository Module

Repository の実装を登録:

```kotlin
// di/RepositoryModule.kt
package com.example.di

import com.example.data.repository.*
import com.example.domain.repository.*
import org.koin.dsl.module

val repositoryModule = module {
    // DAO
    single { UserDao() }
    single { VideoDao() }
    single { CommentDao() }

    // Repository（インターフェースに対して実装をバインド）
    single<UserRepository> { UserRepositoryImpl(get()) }
    single<VideoRepository> { VideoRepositoryImpl(get()) }
    single<CommentRepository> { CommentRepositoryImpl(get()) }

    // Auth Repository（設定値を使用）
    single<AuthRepository> {
        val jwtConfig = get<JwtConfig>()
        AuthRepositoryImpl(
            jwtSecret = jwtConfig.secret,
            jwtIssuer = jwtConfig.issuer,
            jwtAudience = jwtConfig.audience,
            accessTokenExpireMinutes = jwtConfig.accessTokenExpireMinutes,
            refreshTokenExpireDays = jwtConfig.refreshTokenExpireDays
        )
    }
}
```

### 3. UseCase Module

UseCase を登録:

```kotlin
// di/UseCaseModule.kt
package com.example.di

import com.example.domain.usecase.auth.*
import com.example.domain.usecase.user.*
import com.example.domain.usecase.video.*
import com.example.domain.usecase.comment.*
import org.koin.dsl.module

val useCaseModule = module {
    // Auth UseCases
    factory { RegisterUseCase(get(), get()) }
    factory { LoginUseCase(get(), get()) }
    factory { RefreshTokenUseCase(get(), get()) }

    // User UseCases
    factory { GetUserUseCase(get()) }
    factory { GetCurrentUserUseCase(get()) }
    factory { UpdateUserUseCase(get()) }
    factory { FollowUserUseCase(get()) }
    factory { UnfollowUserUseCase(get()) }

    // Video UseCases
    factory { GetFeedUseCase(get()) }
    factory { GetVideoUseCase(get()) }
    factory { LikeVideoUseCase(get()) }
    factory { UnlikeVideoUseCase(get()) }
    factory { ShareVideoUseCase(get()) }

    // Comment UseCases
    factory { GetCommentsUseCase(get(), get()) }
    factory { PostCommentUseCase(get(), get()) }
    factory { LikeCommentUseCase(get()) }
    factory { UnlikeCommentUseCase(get()) }
}
```

## スコープの使い分け

| スコープ | 用途 | 例 |
|---------|------|-----|
| `single` | シングルトン（アプリケーション全体で1インスタンス） | Repository, Config |
| `factory` | 毎回新しいインスタンス | UseCase |
| `scoped` | 特定のスコープ内で共有 | リクエストスコープ |

```kotlin
val repositoryModule = module {
    // シングルトン: DB接続を共有
    single<UserRepository> { UserRepositoryImpl(get()) }

    // ファクトリー: 状態を持つ可能性があるためリクエストごとに作成
    factory { CreateUserUseCase(get()) }
}
```

## Routes での使用

### inject() による取得

```kotlin
// routes/UserRoutes.kt
import org.koin.ktor.ext.inject

fun Route.userRoutes() {
    // by inject で遅延注入
    val getUserUseCase by inject<GetUserUseCase>()
    val createUserUseCase by inject<CreateUserUseCase>()
    val updateUserUseCase by inject<UpdateUserUseCase>()

    route("/api/v1/users") {
        get("/{id}") {
            val userId = call.parameters["id"]!!
            val user = getUserUseCase(UserId(userId))
            call.respond(user)
        }

        post {
            val request = call.receive<CreateUserRequest>()
            val user = createUserUseCase(request.toInput())
            call.respond(HttpStatusCode.Created, user)
        }
    }
}
```

### get() による即時取得

```kotlin
fun Route.userRoutes() {
    val koin = getKoin()

    route("/api/v1/users") {
        get("/{id}") {
            // リクエストごとに取得
            val useCase = koin.get<GetUserUseCase>()
            // ...
        }
    }
}
```

## テストでの DI

### テスト用モジュール

```kotlin
// test/di/TestModules.kt
val testRepositoryModule = module {
    single<UserRepository> { FakeUserRepository() }
    single<AuthRepository> { FakeAuthRepository() }
}

val testUseCaseModule = module {
    factory { GetUserUseCase(get()) }
    factory { CreateUserUseCase(get()) }
}
```

### テストでの使用

```kotlin
class UserUseCaseTest : FunSpec({
    beforeTest {
        startKoin {
            modules(testRepositoryModule, testUseCaseModule)
        }
    }

    afterTest {
        stopKoin()
    }

    test("should get user by id") {
        val useCase = getKoin().get<GetUserUseCase>()
        val result = useCase(UserId("test-id"))
        result.shouldBeSuccess()
    }
})
```

### MockK との併用

```kotlin
class UserUseCaseTest : FunSpec({
    val mockUserRepository = mockk<UserRepository>()

    beforeTest {
        startKoin {
            modules(
                module {
                    single { mockUserRepository }
                    factory { GetUserUseCase(get()) }
                }
            )
        }
    }

    afterTest {
        stopKoin()
        clearMocks(mockUserRepository)
    }

    test("should return user") {
        val expectedUser = User(...)
        coEvery { mockUserRepository.findById(any()) } returns expectedUser

        val useCase = getKoin().get<GetUserUseCase>()
        val result = useCase(UserId("test-id"))

        result shouldBe expectedUser
        coVerify { mockUserRepository.findById(UserId("test-id")) }
    }
})
```

## ベストプラクティス

### 1. インターフェースに対してバインド

```kotlin
// ✅ 良い例: インターフェースに対してバインド
single<UserRepository> { UserRepositoryImpl(get()) }

// ❌ 悪い例: 実装クラスに対してバインド
single { UserRepositoryImpl(get()) }
```

### 2. 循環依存を避ける

```kotlin
// ❌ 循環依存
class ServiceA(private val serviceB: ServiceB)
class ServiceB(private val serviceA: ServiceA)

// ✅ 解決策: 共通の依存を抽出
class SharedService()
class ServiceA(private val shared: SharedService)
class ServiceB(private val shared: SharedService)
```

### 3. モジュールの分割

```kotlin
// ❌ 大きすぎるモジュール
val appModule = module {
    single { ... }
    single { ... }
    // 100行以上...
}

// ✅ 機能ごとに分割
val authModule = module { ... }
val userModule = module { ... }
val videoModule = module { ... }

// Application.kt
modules(authModule, userModule, videoModule)
```

### 4. 設定値の注入

```kotlin
// ✅ 設定を DI 経由で取得
single<AuthRepository> {
    val config = get<JwtConfig>()
    AuthRepositoryImpl(
        jwtSecret = config.secret,
        jwtIssuer = config.issuer
    )
}

// ❌ 直接環境変数を参照
single<AuthRepository> {
    AuthRepositoryImpl(
        jwtSecret = System.getenv("JWT_SECRET")!!  // テスト困難
    )
}
```

## トラブルシューティング

### "No definition found" エラー

```
org.koin.core.error.NoBeanDefFoundException: No definition found for class
```

**解決策**:
1. モジュールが登録されているか確認
2. インターフェースと実装の型が一致しているか確認
3. 依存関係の順序を確認

### 循環依存エラー

```
Circular dependency detected
```

**解決策**:
1. 設計を見直し、共通の依存を抽出
2. `lazy` を使用して遅延初期化
3. Provider パターンを使用
