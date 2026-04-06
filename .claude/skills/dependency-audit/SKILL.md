---
name: dependency-audit
description: >
  Gradle 依存関係を監査するスキル。脆弱性、陳腐化した依存、
  ライセンス互換性をチェックする。
  トリガー: "依存関係チェック", "脆弱性スキャン", "依存関係監査"
---

# Dependency Audit

Gradle 依存関係の監査とセキュリティチェック。

## 実行方法

### Gradle タスク

```bash
# 依存関係ツリー表示
./gradlew dependencies

# アウトデート確認
./gradlew dependencyUpdates

# 脆弱性スキャン（OWASP Dependency Check）
./gradlew dependencyCheckAnalyze
```

### 監査スクリプト

```bash
python .claude/skills/dependency-audit/scripts/audit_dependencies.py
```

## チェック項目

### 1. セキュリティ脆弱性

既知の CVE を含む依存関係を検出:

```
🔴 Critical: CVE-2023-XXXXX in library:version
   影響: リモートコード実行の可能性
   対策: version X.X.X 以上にアップデート
```

### 2. 陳腐化した依存関係

6ヶ月以上更新されていない依存関係:

```
🟡 Warning: library:1.0.0 (最終更新: 2023-01-01)
   最新バージョン: 2.0.0
   推奨: アップデートを検討
```

### 3. 非推奨ライブラリ

```
⚠️  Deprecated: jcenter() リポジトリ
   対策: mavenCentral() に移行
```

### 4. ライセンス互換性

```
📄 License Check:
   ✅ Apache-2.0: compatible
   ✅ MIT: compatible
   ⚠️  GPL-3.0: 要確認（コピーレフト）
```

## 推奨バージョン（2024年時点）

| ライブラリ | 推奨バージョン |
|-----------|--------------|
| Ktor | 3.0.x |
| Kotlin | 2.0.x |
| Exposed | 0.50.x |
| Koin | 3.5.x |
| kotlinx.serialization | 1.6.x |
| Kotest | 5.8.x |
| MockK | 1.13.x |

## build.gradle.kts 設定

### 脆弱性チェックプラグイン

```kotlin
plugins {
    id("org.owasp.dependencycheck") version "9.0.9"
}

dependencyCheck {
    failBuildOnCVSS = 7.0f
    suppressionFile = "owasp-suppressions.xml"
}
```

### バージョンカタログ

```kotlin
// gradle/libs.versions.toml
[versions]
ktor = "3.0.0"
kotlin = "2.0.0"
exposed = "0.50.1"
koin = "3.5.3"

[libraries]
ktor-server-core = { module = "io.ktor:ktor-server-core", version.ref = "ktor" }
```

## 対応フロー

```
1. 監査実行
   ↓
2. 問題の分類
   - Critical: 即時対応
   - Warning: 計画的対応
   - Info: 把握のみ
   ↓
3. アップデート計画作成
   ↓
4. テスト実行
   ↓
5. デプロイ
```

## Reference

- [Vulnerability Database](reference/vulnerability-database.md)
