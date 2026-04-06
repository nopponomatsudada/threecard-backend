# Ktor Checklist

## Plugin 設定

- [ ] ContentNegotiation (JSON) が設定されている
- [ ] StatusPages でエラーハンドリング
- [ ] Authentication が設定されている
- [ ] CORS が設定されている（必要な場合）
- [ ] CallLogging が設定されている（推奨）

## ルーティング

- [ ] route() でグループ化されている
- [ ] HTTP メソッドが RESTful に使用されている
- [ ] パスパラメータが適切に使用されている
- [ ] クエリパラメータのデフォルト値が設定されている

## 非同期処理

- [ ] suspend 関数が適切に使用されている
- [ ] Dispatchers.IO でブロッキング処理を実行
- [ ] タイムアウトが設定されている

## レスポンス

- [ ] 適切な HTTP ステータスコードを返している
- [ ] Content-Type が正しく設定されている
- [ ] 成功時とエラー時のレスポンス形式が統一されている

## テスト

- [ ] testApplication を使用して API テスト
- [ ] モック UseCase でテスト
- [ ] 認証が必要なエンドポイントのテスト

## 例

```kotlin
// ✅ Good Pattern
fun Application.module() {
    install(ContentNegotiation) { json() }
    install(StatusPages) {
        exception<DomainError> { call, cause ->
            call.respond(cause.toHttpStatusCode(), ErrorResponse(cause))
        }
    }
    install(Authentication) {
        jwt("jwt") { /* config */ }
    }

    routing {
        authenticate("jwt") {
            route("/api/v1") {
                userRoutes()
            }
        }
    }
}
```
