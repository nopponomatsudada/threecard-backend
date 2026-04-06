# Module Scaffolding Guide

## 概要

新規機能を追加する際の実装手順です。Clean Architecture に従い、Domain → Data → Routes の順で実装します。

## 実装フロー

```
┌─────────────────────────────────────────────────────────────┐
│  Step 0: 依存関係チェック（初回実装時のみ）                    │
│  - libs.versions.toml のバージョンを最新安定版と比較          │
│  - セキュリティ脆弱性のある依存関係を検出・修正               │
│  - 破壊的変更がある場合は移行ガイドを確認                     │
│  - アップグレード後にビルド検証                               │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│  Step 1: Domain 層                                          │
│  - Entity, ValueObject 定義                                 │
│  - Repository インターフェース定義                           │
│  - UseCase 実装                                             │
│  - DomainError 定義                                         │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│  Step 2: Data 層                                            │
│  - Exposed Table 定義                                       │
│  - DAO 実装                                                 │
│  - Repository 実装                                          │
│  - Mapper 実装                                              │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│  Step 3: Routes 層                                          │
│  - Request/Response DTO 定義                                │
│  - Route 定義                                               │
│  - バリデーション実装                                        │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│  Step 4: DI 設定                                            │
│  - Koin Module 登録                                         │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│  Step 5: テスト作成                                          │
│  - Unit Tests (Domain)                                      │
│  - Integration Tests (Data)                                 │
│  - API Tests (Routes)                                       │
└─────────────────────────────────────────────────────────────┘
```

## Step 0: 依存関係チェック（初回実装時のみ）

プロジェクトの初回実装開始前、または長期中断後の再開時に実施する。
機能追加のたびに毎回実施する必要はない。

### 0.1 バージョン確認

```bash
# 依存関係ツリーを確認
./gradlew dependencies --configuration runtimeClasspath
```

`gradle/libs.versions.toml` の各ライブラリについて、最新安定版を確認する。
`/backend-dependency-audit` スキルで自動チェックも可能。

### 0.2 セキュリティ脆弱性チェック

各ライブラリの現行バージョンに既知の CVE がないか確認する:
- [GitHub Advisory Database](https://github.com/advisories)
- [National Vulnerability Database](https://nvd.nist.gov/)
- `/backend-security-audit` スキル

### 0.3 アップグレード実施

1. `gradle/libs.versions.toml` のバージョンを更新
2. **メジャーバージョンアップの場合は必ず移行ガイドを確認**
   - 例: Exposed 0.x → 1.0 ではパッケージ名が `org.jetbrains.exposed.sql.*` → `org.jetbrains.exposed.v1.core/jdbc.*` に変更
3. ビルド検証: `./gradlew build`
4. コンパイルエラーがあれば移行ガイドに従って修正

> **なぜ実装前に実施するか**: メジャーバージョンの破壊的変更（パッケージ名変更、API 変更等）は
> 実装済みファイル全てに影響する。実装後にアップグレードすると大規模リファクタリングが必要になる。

## Step 1: Domain 層

### 1.0 共通ガードレール（重要）

以下は機能種別に関わらず必須の実装ルールです。

- **金額は `Double` を使わない**（誤差が出るため）  
  入力は `String` または最小通貨単位の `Long` で受け取り、`BigDecimal` に変換する。
- **残高を変更する処理は必ず 1 トランザクション内で完結させる**  
  例: 残高更新 + 取引履歴保存は同一トランザクションで実行する。
- **認証/認可系の実装では access/refresh トークンの区別を必須化**  
  API 認証は access token のみを許可し、refresh token は拒否する。
- **パスワードリセットは必ずユーザー更新を永続化する**  
  検証だけで成功返却しない。

### 1.1 Entity 作成

```kotlin
// domain/model/entity/Task.kt
data class Task(
    val id: TaskId,
    val userId: UserId,
    val title: TaskTitle,
    val description: TaskDescription?,
    val status: TaskStatus,
    val dueDate: DueDate?,
    val createdAt: Instant,
    val updatedAt: Instant
) {
    fun complete(): Task = copy(
        status = TaskStatus.COMPLETED,
        updatedAt = Instant.now()
    )

    fun reopen(): Task = copy(
        status = TaskStatus.PENDING,
        updatedAt = Instant.now()
    )

    companion object {
        fun create(
            userId: UserId,
            title: TaskTitle,
            description: TaskDescription? = null,
            dueDate: DueDate? = null
        ): Task = Task(
            id = TaskId.generate(),
            userId = userId,
            title = title,
            description = description,
            status = TaskStatus.PENDING,
            dueDate = dueDate,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
    }
}

enum class TaskStatus {
    PENDING, IN_PROGRESS, COMPLETED, CANCELLED
}
```

### 1.2 ValueObject 作成

```kotlin
// domain/model/valueobject/TaskId.kt
@JvmInline
value class TaskId(val value: String) {
    companion object {
        fun generate(): TaskId = TaskId(UUID.randomUUID().toString())
    }
}

// domain/model/valueobject/TaskTitle.kt
@JvmInline
value class TaskTitle private constructor(val value: String) {
    companion object {
        private const val MAX_LENGTH = 100

        fun create(value: String): Result<TaskTitle> {
            val trimmed = value.trim()
            return when {
                trimmed.isBlank() -> Result.failure(DomainError.TaskTitleRequired)
                trimmed.length > MAX_LENGTH -> Result.failure(DomainError.TaskTitleTooLong(MAX_LENGTH))
                else -> Result.success(TaskTitle(trimmed))
            }
        }
    }
}
```

### 1.3 Repository インターフェース

```kotlin
// domain/repository/TaskRepository.kt
interface TaskRepository {
    suspend fun findById(id: TaskId): Task?
    suspend fun findByUserId(userId: UserId, limit: Int = 100, offset: Int = 0): List<Task>
    suspend fun save(task: Task): Task
    suspend fun delete(id: TaskId)
}
```

### 1.4 UseCase 作成

```kotlin
// domain/usecase/CreateTaskUseCase.kt
class CreateTaskUseCase(private val taskRepository: TaskRepository) {
    suspend operator fun invoke(
        userId: UserId,
        title: TaskTitle,
        description: TaskDescription? = null,
        dueDate: DueDate? = null
    ): Result<Task> {
        val task = Task.create(userId, title, description, dueDate)
        return Result.success(taskRepository.save(task))
    }
}

// domain/usecase/GetTaskUseCase.kt
class GetTaskUseCase(private val taskRepository: TaskRepository) {
    suspend operator fun invoke(id: TaskId): Result<Task> {
        val task = taskRepository.findById(id)
            ?: return Result.failure(DomainError.TaskNotFound(id))
        return Result.success(task)
    }
}

// domain/usecase/CompleteTaskUseCase.kt
class CompleteTaskUseCase(private val taskRepository: TaskRepository) {
    suspend operator fun invoke(id: TaskId): Result<Task> {
        val task = taskRepository.findById(id)
            ?: return Result.failure(DomainError.TaskNotFound(id))

        if (task.status == TaskStatus.COMPLETED) {
            return Result.failure(DomainError.TaskAlreadyCompleted(id))
        }

        val completed = task.complete()
        return Result.success(taskRepository.save(completed))
    }
}
```

### 1.5 DomainError 追加

```kotlin
// domain/error/DomainError.kt に追加
sealed class DomainError : Exception() {
    // ... 既存のエラー ...

    // Task 関連エラー
    object TaskTitleRequired : DomainError()
    data class TaskTitleTooLong(val maxLength: Int) : DomainError()
    data class TaskNotFound(val id: TaskId) : DomainError()
    data class TaskAlreadyCompleted(val id: TaskId) : DomainError()

    val code: String
        get() = when (this) {
            // ... 既存のコード ...
            is TaskTitleRequired -> "TASK_TITLE_REQUIRED"
            is TaskTitleTooLong -> "TASK_TITLE_TOO_LONG"
            is TaskNotFound -> "TASK_NOT_FOUND"
            is TaskAlreadyCompleted -> "TASK_ALREADY_COMPLETED"
        }
}
```

## Step 2: Data 層

### 2.1 Exposed Table 定義

> **重要**: FK には必ず `onDelete = ReferenceOption.CASCADE` を指定する。
> 省略するとユーザー削除時に FK 制約違反（500 エラー）が発生する。

```kotlin
// data/entity/TaskTable.kt
object TaskTable : Table("tasks") {
    val id = varchar("id", 36)
    val userId = varchar("user_id", 36).references(UserTable.id, onDelete = ReferenceOption.CASCADE)
    val title = varchar("title", 100)
    val description = text("description").nullable()
    val status = enumerationByName<TaskStatus>("status", 20)
    val dueDate = date("due_date").nullable()
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")

    override val primaryKey = PrimaryKey(id)

    init {
        index(false, userId)
        index(false, status)
    }
}

fun ResultRow.toTask(): Task = Task(
    id = TaskId(this[TaskTable.id]),
    userId = UserId(this[TaskTable.userId]),
    title = TaskTitle.create(this[TaskTable.title]).getOrThrow(),
    description = this[TaskTable.description]?.let { TaskDescription.create(it).getOrNull() },
    status = this[TaskTable.status],
    dueDate = this[TaskTable.dueDate]?.let { DueDate(it) },
    createdAt = this[TaskTable.createdAt],
    updatedAt = this[TaskTable.updatedAt]
)
```

### 2.2 DAO 実装

```kotlin
// data/dao/TaskDao.kt
class TaskDao {
    suspend fun findById(id: TaskId): Task? = dbQuery {
        TaskTable.selectAll()
            .where { TaskTable.id eq id.value }
            .map { it.toTask() }
            .singleOrNull()
    }

    suspend fun findByUserId(userId: UserId, limit: Int, offset: Int): List<Task> = dbQuery {
        TaskTable.selectAll()
            .where { TaskTable.userId eq userId.value }
            .orderBy(TaskTable.createdAt, SortOrder.DESC)
            .limit(limit)
            .offset(offset.toLong())
            .map { it.toTask() }
    }

    suspend fun insert(task: Task): Task = dbQuery {
        TaskTable.insert {
            it[id] = task.id.value
            it[userId] = task.userId.value
            it[title] = task.title.value
            it[description] = task.description?.value
            it[status] = task.status
            it[dueDate] = task.dueDate?.value
            it[createdAt] = task.createdAt
            it[updatedAt] = task.updatedAt
        }
        task
    }

    suspend fun update(task: Task): Task = dbQuery {
        TaskTable.update({ TaskTable.id eq task.id.value }) {
            it[title] = task.title.value
            it[description] = task.description?.value
            it[status] = task.status
            it[dueDate] = task.dueDate?.value
            it[updatedAt] = task.updatedAt
        }
        task
    }

    suspend fun delete(id: TaskId) = dbQuery {
        TaskTable.deleteWhere { TaskTable.id eq id.value }
    }
}
```

### 2.3 Repository 実装

```kotlin
// data/repository/TaskRepositoryImpl.kt
class TaskRepositoryImpl(private val dao: TaskDao) : TaskRepository {
    override suspend fun findById(id: TaskId): Task? = dao.findById(id)

    override suspend fun findByUserId(userId: UserId, limit: Int, offset: Int): List<Task> =
        dao.findByUserId(userId, limit, offset)

    override suspend fun save(task: Task): Task {
        return if (dao.findById(task.id) != null) {
            dao.update(task)
        } else {
            dao.insert(task)
        }
    }

    override suspend fun delete(id: TaskId) = dao.delete(id)
}
```

## Step 3: Routes 層

### 3.1 DTO 定義

```kotlin
// routes/dto/request/CreateTaskRequest.kt
@Serializable
data class CreateTaskRequest(
    val title: String,
    val description: String? = null,
    val dueDate: String? = null
)

// routes/dto/response/TaskResponse.kt
@Serializable
data class TaskResponse(
    val id: String,
    val userId: String,
    val title: String,
    val description: String?,
    val status: String,
    val dueDate: String?,
    val createdAt: String,
    val updatedAt: String
) {
    companion object {
        fun from(task: Task): TaskResponse = TaskResponse(
            id = task.id.value,
            userId = task.userId.value,
            title = task.title.value,
            description = task.description?.value,
            status = task.status.name,
            dueDate = task.dueDate?.value?.toString(),
            createdAt = task.createdAt.toString(),
            updatedAt = task.updatedAt.toString()
        )
    }
}
```

**入力値の型とバリデーション**

- 金額は `Double` を避け、`String` or `Long` で受け取る  
  例: `amount: String` を `BigDecimal` へ変換して検証
- 0 以下の金額は即時 `BadRequest`
- enum などは `valueOf` の例外を捕捉して `BadRequest`

### 3.2 Route 定義

```kotlin
// routes/TaskRoutes.kt
fun Route.taskRoutes() {
    val createTaskUseCase by inject<CreateTaskUseCase>()
    val getTaskUseCase by inject<GetTaskUseCase>()
    val listTasksUseCase by inject<ListTasksUseCase>()
    val completeTaskUseCase by inject<CompleteTaskUseCase>()
    val deleteTaskUseCase by inject<DeleteTaskUseCase>()

    route("/api/v1/tasks") {
        get {
            val userId = call.principal<JWTPrincipal>()?.userId()
                ?: return@get call.respond(HttpStatusCode.Unauthorized)

            val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 20
            val offset = call.request.queryParameters["offset"]?.toIntOrNull() ?: 0

            val tasks = listTasksUseCase(userId, limit, offset)
            call.respond(ApiResponse(data = tasks.map { TaskResponse.from(it) }))
        }

        get("/{id}") {
            val id = call.parameters["id"]
                ?: return@get call.respond(HttpStatusCode.BadRequest)

            getTaskUseCase(TaskId(id))
                .onSuccess { call.respond(ApiResponse(data = TaskResponse.from(it))) }
                .onFailure { call.respondError(it) }
        }

        post {
            val userId = call.principal<JWTPrincipal>()?.userId()
                ?: return@post call.respond(HttpStatusCode.Unauthorized)

            val request = call.receive<CreateTaskRequest>()

            val title = TaskTitle.create(request.title).getOrElse {
                return@post call.respondError(it)
            }

            createTaskUseCase(userId, title, null, null)
                .onSuccess { call.respond(HttpStatusCode.Created, ApiResponse(data = TaskResponse.from(it))) }
                .onFailure { call.respondError(it) }
        }

        post("/{id}/complete") {
            val id = call.parameters["id"]
                ?: return@post call.respond(HttpStatusCode.BadRequest)

            completeTaskUseCase(TaskId(id))
                .onSuccess { call.respond(ApiResponse(data = TaskResponse.from(it))) }
                .onFailure { call.respondError(it) }
        }

        delete("/{id}") {
            val id = call.parameters["id"]
                ?: return@delete call.respond(HttpStatusCode.BadRequest)

            deleteTaskUseCase(TaskId(id))
                .onSuccess { call.respond(HttpStatusCode.NoContent) }
                .onFailure { call.respondError(it) }
        }
    }
}
```

## Step 4: DI 設定

```kotlin
// plugins/Koin.kt に追加
val taskModule = module {
    // DAO
    single { TaskDao() }

    // Repository
    single<TaskRepository> { TaskRepositoryImpl(get()) }

    // UseCase
    single { CreateTaskUseCase(get()) }
    single { GetTaskUseCase(get()) }
    single { ListTasksUseCase(get()) }
    single { CompleteTaskUseCase(get()) }
    single { DeleteTaskUseCase(get()) }
}

// Application.kt で登録
install(Koin) {
    modules(
        userModule,
        taskModule  // 追加
    )
}
```

## Step 5: テスト作成

### 5.1 Domain テスト

```kotlin
class CreateTaskUseCaseTest : FunSpec({
    test("should create task with valid input") {
        val repository = InMemoryTaskRepository()
        val useCase = CreateTaskUseCase(repository)

        val result = useCase(
            userId = UserId("user-1"),
            title = TaskTitle.create("Test Task").getOrThrow()
        )

        result.isSuccess shouldBe true
        result.getOrNull()?.title?.value shouldBe "Test Task"
    }
})
```

### 5.2 Data テスト

```kotlin
class TaskRepositoryImplTest : FunSpec({
    beforeSpec { TestDatabase.setup() }
    afterSpec { TestDatabase.teardown() }
    beforeTest { TestDatabase.clear() }

    test("should save and retrieve task") {
        val repository = TaskRepositoryImpl(TaskDao())
        val task = createTestTask()

        repository.save(task)
        val found = repository.findById(task.id)

        found shouldBe task
    }
})
```

### 5.3 Routes テスト

```kotlin
class TaskRoutesTest : FunSpec({
    test("POST /api/v1/tasks should create task") {
        testApplication {
            // ... テスト設定 ...

            val response = client.post("/api/v1/tasks") {
                header(HttpHeaders.Authorization, "Bearer $testToken")
                contentType(ContentType.Application.Json)
                setBody("""{"title": "New Task"}""")
            }

            response.status shouldBe HttpStatusCode.Created
        }
    }
})
```

## チェックリスト

### Domain 層
- [ ] Entity を作成した
- [ ] 必要な ValueObject を作成した
- [ ] Repository インターフェースを定義した
- [ ] UseCase を作成した
- [ ] DomainError を追加した
- [ ] Unit テストを作成した

### Data 層
- [ ] Exposed Table を定義した
- [ ] **FK に `onDelete = ReferenceOption.CASCADE` を指定した**
- [ ] DAO を実装した（`deleteWhere` は Int を返すため、`: Unit` 戻り値では末尾に `Unit` を明記）
- [ ] Repository を実装した
- [ ] サブリソース DELETE にオーナーシップ検証チェーンを実装した（該当する場合）
- [ ] Integration テストを作成した

### Routes 層
- [ ] Request DTO を定義した
- [ ] Response DTO を定義した
- [ ] Route を定義した
- [ ] API テストを作成した

### 共通
- [ ] Koin Module に登録した
- [ ] 全テストが通過した
- [ ] コードレビューを受けた
