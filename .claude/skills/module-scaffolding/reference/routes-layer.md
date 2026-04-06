# Routes Layer Reference

## 必須インポート

> **重要**: Route ハンドラで `call` を使用するには `io.ktor.server.application.*` が必須です。

```kotlin
package com.example.routes

import io.ktor.http.*
import io.ktor.server.application.*   // ← 必須: call の解決に必要
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import org.koin.ktor.ext.inject
```

## Request DTO

```kotlin
@Serializable
data class CreateEntityRequest(
    val name: String,
    val description: String? = null
) {
    fun validate(): List<String> {
        val errors = mutableListOf<String>()
        if (name.isBlank()) errors.add("name is required")
        return errors
    }
}
```

## Response DTO

```kotlin
@Serializable
data class EntityResponse(
    val id: String,
    val name: String,
    val status: String,
    val createdAt: String,
    val updatedAt: String
) {
    companion object {
        fun from(entity: Entity): EntityResponse = EntityResponse(
            id = entity.id.value,
            name = entity.name.value,
            status = entity.status.name,
            createdAt = entity.createdAt.toString(),
            updatedAt = entity.updatedAt.toString()
        )
    }
}
```

## Route 定義

```kotlin
fun Route.entityRoutes() {
    val getUseCase by inject<GetEntityUseCase>()
    val createUseCase by inject<CreateEntityUseCase>()
    val updateUseCase by inject<UpdateEntityUseCase>()
    val deleteUseCase by inject<DeleteEntityUseCase>()

    route("/api/v1/entities") {
        // GET /api/v1/entities
        get {
            val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 20
            val offset = call.request.queryParameters["offset"]?.toIntOrNull() ?: 0
            // ...
        }

        // GET /api/v1/entities/{id}
        get("/{id}") {
            val id = call.parameters["id"]
                ?: return@get call.respond(HttpStatusCode.BadRequest)

            getUseCase(EntityId(id))
                .onSuccess { call.respond(ApiResponse(EntityResponse.from(it))) }
                .onFailure { call.respondError(it) }
        }

        // POST /api/v1/entities
        post {
            val request = call.receive<CreateEntityRequest>()

            // バリデーション
            val errors = request.validate()
            if (errors.isNotEmpty()) {
                return@post call.respond(HttpStatusCode.BadRequest, /* error */)
            }

            // UseCase 呼び出し
            createUseCase(/* params */)
                .onSuccess { call.respond(HttpStatusCode.Created, ApiResponse(EntityResponse.from(it))) }
                .onFailure { call.respondError(it) }
        }

        // PUT /api/v1/entities/{id}
        put("/{id}") { /* ... */ }

        // DELETE /api/v1/entities/{id}
        delete("/{id}") { /* ... */ }
    }
}
```

## エラーレスポンス

```kotlin
suspend fun ApplicationCall.respondError(error: Throwable) {
    when (error) {
        is DomainError -> {
            respond(error.toHttpStatusCode(), ErrorResponse(error.code, error.message))
        }
        else -> {
            respond(HttpStatusCode.InternalServerError, ErrorResponse("INTERNAL_ERROR", "An error occurred"))
        }
    }
}
```
