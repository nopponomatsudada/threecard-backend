# Domain Layer Reference

## Entity パターン

```kotlin
data class Entity(
    val id: EntityId,
    val status: EntityStatus,
    val createdAt: Instant,
    val updatedAt: Instant
) {
    // ビジネスメソッドは新しいインスタンスを返す
    fun activate(): Entity = copy(
        status = EntityStatus.ACTIVE,
        updatedAt = Instant.now()
    )

    companion object {
        fun create(/* params */): Entity = Entity(
            id = EntityId.generate(),
            status = EntityStatus.PENDING,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
    }
}
```

## ValueObject パターン

### ID 型

```kotlin
@JvmInline
value class EntityId(val value: String) {
    companion object {
        fun generate(): EntityId = EntityId(UUID.randomUUID().toString())
    }
}
```

### バリデーション付き ValueObject

```kotlin
@JvmInline
value class Email private constructor(val value: String) {
    companion object {
        fun create(value: String): Result<Email> {
            return if (isValid(value)) {
                Result.success(Email(value))
            } else {
                Result.failure(DomainError.InvalidEmail(value))
            }
        }

        private fun isValid(value: String): Boolean = /* validation */
    }
}
```

## UseCase パターン

```kotlin
class CreateEntityUseCase(
    private val repository: EntityRepository
) {
    suspend operator fun invoke(params: Params): Result<Entity> {
        // 1. バリデーション
        // 2. ビジネスルールチェック
        // 3. Entity 作成
        // 4. 永続化
        // 5. Result 返却
    }
}
```

## DomainError パターン

```kotlin
sealed class DomainError : Exception() {
    // バリデーションエラー
    data class InvalidInput(val field: String, val reason: String) : DomainError()

    // ビジネスルールエラー
    data class EntityNotFound(val id: EntityId) : DomainError()
    data class DuplicateEntity(val key: String) : DomainError()

    val code: String get() = /* error code */
    override val message: String get() = /* message */
}
```
