# ビルド設定ガイド

## build.gradle.kts テンプレート

### 基本構成

```kotlin
plugins {
    kotlin("jvm") version "2.0.21"
    kotlin("plugin.serialization") version "2.0.21"
    id("io.ktor.plugin") version "3.0.3"
    id("com.gradleup.shadow") version "8.3.5"
}

group = "com.example"
version = "0.0.1"

application {
    mainClass.set("com.example.ApplicationKt")

    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

// JVM ツールチェーン設定（重要）
kotlin {
    jvmToolchain(21)  // プロジェクトで使用する JVM バージョン
}

repositories {
    mavenCentral()
}
```

### 依存関係

```kotlin
dependencies {
    // Ktor Server
    val ktorVersion = "3.0.3"
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
    implementation("io.ktor:ktor-server-auth:$ktorVersion")
    implementation("io.ktor:ktor-server-auth-jwt:$ktorVersion")
    implementation("io.ktor:ktor-server-status-pages:$ktorVersion")
    implementation("io.ktor:ktor-server-cors:$ktorVersion")
    implementation("io.ktor:ktor-server-call-logging:$ktorVersion")

    // Exposed ORM
    val exposedVersion = "0.57.0"
    implementation("org.jetbrains.exposed:exposed-core:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-dao:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-java-time:$exposedVersion")

    // Database Drivers
    implementation("org.postgresql:postgresql:42.7.4")
    implementation("com.h2database:h2:2.3.232")  // テスト用

    // Dependency Injection
    val koinVersion = "4.0.0"
    implementation("io.insert-koin:koin-ktor:$koinVersion")
    implementation("io.insert-koin:koin-logger-slf4j:$koinVersion")

    // Password Hashing
    implementation("at.favre.lib:bcrypt:0.10.2")

    // Logging
    implementation("ch.qos.logback:logback-classic:1.5.12")

    // Testing
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")
    testImplementation("io.kotest:kotest-runner-junit5:5.9.1")
    testImplementation("io.kotest:kotest-assertions-core:5.9.1")
    testImplementation("io.mockk:mockk:1.13.13")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
```

## よくある問題

### 1. JVM バージョン不一致

```
Inconsistent JVM-target compatibility detected for tasks 'compileJava' (21) and 'compileKotlin' (17)
```

**解決策**: `kotlin { jvmToolchain(21) }` を使用

```kotlin
// ❌ 古い書き方
tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "17"
}

// ✅ 新しい書き方
kotlin {
    jvmToolchain(21)
}
```

### 2. Shadow Plugin エラー

```
Could not find com.github.johnrengelman.shadow
```

**解決策**: 新しいプラグイン ID を使用

```kotlin
// ❌ 古いプラグイン
id("com.github.johnrengelman.shadow") version "8.1.1"

// ✅ 新しいプラグイン
id("com.gradleup.shadow") version "8.3.5"
```

### 3. Exposed バージョン互換性

Exposed 0.40+ では一部 API が変更されています：

| 機能 | 旧 API | 新 API |
|------|--------|--------|
| ページネーション | `.limit(n).offset(m)` | `.limit(n, m)` |
| WHERE 句 | `.select { ... }` | `.selectAll().where { ... }` |

## Gradle Wrapper

### 生成方法

```bash
# Gradle Wrapper を生成
gradle wrapper --gradle-version 8.11

# 確認
./gradlew --version
```

### gradle-wrapper.properties

```properties
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-8.11-bin.zip
networkTimeout=10000
validateDistributionUrl=true
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
```

## gradle.properties

```properties
# Kotlin
kotlin.code.style=official

# Gradle
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
org.gradle.parallel=true
org.gradle.caching=true

# Ktor
ktor.deployment.port=8080
```

## バージョン互換性マトリックス

| Kotlin | Ktor | Exposed | Koin | 推奨 JVM |
|--------|------|---------|------|----------|
| 2.0.x | 3.0.x | 0.55+ | 4.0.x | 21 |
| 1.9.x | 2.3.x | 0.46-0.54 | 3.5.x | 17-21 |
| 1.8.x | 2.2.x | 0.40-0.45 | 3.4.x | 11-17 |

## 推奨設定

最新の安定構成:

```kotlin
plugins {
    kotlin("jvm") version "2.0.21"
    kotlin("plugin.serialization") version "2.0.21"
    id("io.ktor.plugin") version "3.0.3"
}

kotlin {
    jvmToolchain(21)
}

val ktorVersion = "3.0.3"
val exposedVersion = "0.57.0"
val koinVersion = "4.0.0"
```
