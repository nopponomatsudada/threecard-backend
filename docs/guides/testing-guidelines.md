# テストガイドライン

## テスト戦略

### テストピラミッド

```
        /\
       /  \        E2E Tests (少数)
      /────\       - API 全体の動作確認
     /      \
    /────────\     Integration Tests (中程度)
   /          \    - Repository + Database
  /────────────\
 /              \  Unit Tests (多数)
/────────────────\ - UseCase, Entity, ValueObject
```

### レイヤー別テスト方針

| レイヤー | テスト種別 | モック対象 | カバレッジ目標 |
|---------|----------|----------|--------------|
| Domain | Unit Test | なし | 90%+ |
| Data | Integration Test | Database (H2) | 80%+ |
| Routes | API Test | UseCase | 80%+ |

## テストフレームワーク

- **Kotest**: テストフレームワーク
- **MockK**: モッキング
- **Ktor Test**: API テスト
- **H2**: テスト用インメモリ DB

```kotlin
// build.gradle.kts
testImplementation("io.kotest:kotest-runner-junit5:5.8.0")
testImplementation("io.kotest:kotest-assertions-core:5.8.0")
testImplementation("io.mockk:mockk:1.13.9")
testImplementation("io.ktor:ktor-server-test-host:3.0.0")
testImplementation("com.h2database:h2:2.2.224")
```

## Unit Tests

### Entity テスト

```kotlin
class UserTest : FunSpec({
    test("should create user with pending status") {
        val email = Email.create("test@example.com").getOrThrow()
        val name = UserName.create("Test User").getOrThrow()

        val user = User.create(email, name)

        user.status shouldBe UserStatus.PENDING
        user.email shouldBe email
        user.name shouldBe name
    }

    test("should activate user") {
        val user = createTestUser(status = UserStatus.PENDING)

        val activated = user.activate()

        activated.status shouldBe UserStatus.ACTIVE
        activated.updatedAt shouldBeGreaterThan user.updatedAt
    }
})
```

### ValueObject テスト

```kotlin
class EmailTest : FunSpec({
    test("should create email with valid format") {
        val result = Email.create("test@example.com")

        result.isSuccess shouldBe true
        result.getOrNull()?.value shouldBe "test@example.com"
    }

    test("should normalize email to lowercase") {
        val result = Email.create("Test@Example.COM")

        result.getOrNull()?.value shouldBe "test@example.com"
    }

    test("should fail with invalid email format") {
        val result = Email.create("invalid-email")

        result.isFailure shouldBe true
        result.exceptionOrNull() shouldBe instanceOf<DomainError.InvalidEmail>()
    }

    context("invalid email formats") {
        withData(
            "no-at-sign",
            "@no-local-part.com",
            "no-domain@",
            "spaces in@email.com",
            ""
        ) { invalidEmail ->
            Email.create(invalidEmail).isFailure shouldBe true
        }
    }
})
```

### UseCase テスト

```kotlin
class CreateUserUseCaseTest : FunSpec({
    val repository = mockk<UserRepository>()
    val useCase = CreateUserUseCase(repository)

    beforeTest {
        clearMocks(repository)
    }

    test("should create user when email is not taken") {
        val email = Email.create("test@example.com").getOrThrow()
        val name = UserName.create("Test").getOrThrow()

        coEvery { repository.findByEmail(email) } returns null
        coEvery { repository.save(any()) } answers { firstArg() }

        val result = useCase(email, name)

        result.isSuccess shouldBe true
        result.getOrNull()?.email shouldBe email
        coVerify { repository.save(any()) }
    }

    test("should fail when email already exists") {
        val email = Email.create("existing@example.com").getOrThrow()
        val name = UserName.create("Test").getOrThrow()
        val existingUser = createTestUser(email = email)

        coEvery { repository.findByEmail(email) } returns existingUser

        val result = useCase(email, name)

        result.isFailure shouldBe true
        result.exceptionOrNull() shouldBe instanceOf<DomainError.EmailAlreadyExists>()
        coVerify(exactly = 0) { repository.save(any()) }
    }
})
```

## Integration Tests

### Repository テスト

```kotlin
class UserRepositoryImplTest : FunSpec({
    beforeSpec { TestDatabase.setup() }
    afterSpec { TestDatabase.teardown() }
    beforeTest { TestDatabase.clear() }

    val dao = UserDao()
    val repository = UserRepositoryImpl(dao)

    test("should save and find user by id") {
        val user = createTestUser()

        repository.save(user)
        val found = repository.findById(user.id)

        found shouldBe user
    }

    test("should find user by email") {
        val user = createTestUser()
        repository.save(user)

        val found = repository.findByEmail(user.email)

        found shouldBe user
    }

    test("should return null when user not found") {
        val found = repository.findById(UserId("non-existent"))

        found shouldBe null
    }

    test("should update existing user") {
        val user = createTestUser()
        repository.save(user)

        val updated = user.activate()
        repository.save(updated)

        val found = repository.findById(user.id)
        found?.status shouldBe UserStatus.ACTIVE
    }

    test("should delete user") {
        val user = createTestUser()
        repository.save(user)

        repository.delete(user.id)

        repository.findById(user.id) shouldBe null
    }
})
```

### テスト用データベース設定

```kotlin
// test/util/TestDatabase.kt
object TestDatabase {
    fun setup() {
        Database.connect(
            url = "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
            driver = "org.h2.Driver"
        )
        transaction {
            SchemaUtils.create(UserTable)
        }
    }

    fun clear() {
        transaction {
            UserTable.deleteAll()
        }
    }

    fun teardown() {
        transaction {
            SchemaUtils.drop(UserTable)
        }
    }
}
```

## API Tests

### Routes テスト

```kotlin
class UserRoutesTest : FunSpec({
    test("GET /api/v1/users/{id} should return user") {
        val user = createTestUser()
        val getUserUseCase = mockk<GetUserUseCase>()
        coEvery { getUserUseCase(user.id) } returns Result.success(user)

        testApplication {
            application {
                install(Koin) {
                    modules(module {
                        single { getUserUseCase }
                    })
                }
                configureSerialization()
                configureRouting()
            }

            val response = client.get("/api/v1/users/${user.id.value}")

            response.status shouldBe HttpStatusCode.OK
            val body = response.body<ApiResponse<UserResponse>>()
            body.data.id shouldBe user.id.value
        }
    }

    test("GET /api/v1/users/{id} should return 404 when not found") {
        val getUserUseCase = mockk<GetUserUseCase>()
        coEvery { getUserUseCase(any()) } returns Result.failure(DomainError.UserNotFound(UserId("unknown")))

        testApplication {
            application {
                install(Koin) {
                    modules(module {
                        single { getUserUseCase }
                    })
                }
                configureSerialization()
                configureStatusPages()
                configureRouting()
            }

            val response = client.get("/api/v1/users/unknown")

            response.status shouldBe HttpStatusCode.NotFound
        }
    }

    test("POST /api/v1/users should create user") {
        val createUserUseCase = mockk<CreateUserUseCase>()
        val user = createTestUser()
        coEvery { createUserUseCase(any(), any()) } returns Result.success(user)

        testApplication {
            application {
                install(Koin) {
                    modules(module {
                        single { createUserUseCase }
                    })
                }
                configureSerialization()
                configureRouting()
            }

            val response = client.post("/api/v1/users") {
                contentType(ContentType.Application.Json)
                setBody("""{"email": "test@example.com", "name": "Test User"}""")
            }

            response.status shouldBe HttpStatusCode.Created
        }
    }
})
```

## テストユーティリティ

### テストデータファクトリ

```kotlin
// test/util/TestFactory.kt
fun createTestUser(
    id: UserId = UserId.generate(),
    email: Email = Email.create("test@example.com").getOrThrow(),
    name: UserName = UserName.create("Test User").getOrThrow(),
    status: UserStatus = UserStatus.PENDING,
    createdAt: Instant = Instant.now(),
    updatedAt: Instant = Instant.now()
): User = User(
    id = id,
    email = email,
    name = name,
    status = status,
    createdAt = createdAt,
    updatedAt = updatedAt
)
```

### インメモリ Repository

```kotlin
// test/util/InMemoryUserRepository.kt
class InMemoryUserRepository : UserRepository {
    private val users = mutableMapOf<UserId, User>()

    override suspend fun findById(id: UserId): User? = users[id]
    override suspend fun findByEmail(email: Email): User? = users.values.find { it.email == email }
    override suspend fun save(user: User): User {
        users[user.id] = user
        return user
    }
    override suspend fun delete(id: UserId) { users.remove(id) }
    override suspend fun findAll(limit: Int, offset: Int): List<User> =
        users.values.drop(offset).take(limit)

    fun clear() = users.clear()
}
```

## CI/CD でのテスト実行

```yaml
# .github/workflows/test.yml
name: Test

on: [push, pull_request]

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'

      - name: Run tests
        run: ./gradlew test

      - name: Upload coverage
        uses: codecov/codecov-action@v3
```
