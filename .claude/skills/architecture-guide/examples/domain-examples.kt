// Domain Layer Examples

// === Entity ===
data class User(
    val id: UserId,
    val email: Email,
    val name: UserName,
    val status: UserStatus,
    val createdAt: Instant,
    val updatedAt: Instant
) {
    fun activate(): User = copy(
        status = UserStatus.ACTIVE,
        updatedAt = Instant.now()
    )

    companion object {
        fun create(email: Email, name: UserName): User = User(
            id = UserId.generate(),
            email = email,
            name = name,
            status = UserStatus.PENDING,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
    }
}

enum class UserStatus { PENDING, ACTIVE, INACTIVE }

// === ValueObject ===
@JvmInline
value class UserId(val value: String) {
    companion object {
        fun generate(): UserId = UserId(UUID.randomUUID().toString())
    }
}

@JvmInline
value class Email private constructor(val value: String) {
    companion object {
        private val REGEX = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$")

        fun create(value: String): Result<Email> {
            return if (REGEX.matches(value)) {
                Result.success(Email(value.lowercase()))
            } else {
                Result.failure(DomainError.InvalidEmail(value))
            }
        }
    }
}

// === Repository Interface ===
interface UserRepository {
    suspend fun findById(id: UserId): User?
    suspend fun findByEmail(email: Email): User?
    suspend fun save(user: User): User
    suspend fun delete(id: UserId)
}

// === UseCase ===
class GetUserUseCase(private val repository: UserRepository) {
    suspend operator fun invoke(id: UserId): Result<User> {
        val user = repository.findById(id)
            ?: return Result.failure(DomainError.UserNotFound(id))
        return Result.success(user)
    }
}

class CreateUserUseCase(private val repository: UserRepository) {
    suspend operator fun invoke(email: Email, name: UserName): Result<User> {
        repository.findByEmail(email)?.let {
            return Result.failure(DomainError.EmailAlreadyExists(email))
        }
        val user = User.create(email, name)
        return Result.success(repository.save(user))
    }
}

// === DomainError ===
sealed class DomainError : Exception() {
    data class InvalidEmail(val email: String) : DomainError()
    data class UserNotFound(val id: UserId) : DomainError()
    data class EmailAlreadyExists(val email: Email) : DomainError()

    val code: String
        get() = when (this) {
            is InvalidEmail -> "INVALID_EMAIL"
            is UserNotFound -> "USER_NOT_FOUND"
            is EmailAlreadyExists -> "EMAIL_ALREADY_EXISTS"
        }
}
