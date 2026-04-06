# ページネーションガイド

## 概要

API でのページネーション実装パターンを説明します。

## ページネーションの種類

| 方式 | 用途 | メリット | デメリット |
|------|------|---------|-----------|
| オフセット | 一般的なリスト | 実装が簡単、任意のページにジャンプ可能 | 大量データで遅い |
| カーソル | 無限スクロール | パフォーマンスが良い | 任意のページにジャンプ不可 |
| キーセット | 高パフォーマンス | 一貫性が高い | 実装が複雑 |

## オフセットベースページネーション

### 共通モデル

```kotlin
// domain/model/common/PaginatedResult.kt
data class PaginatedResult<T>(
    val items: List<T>,
    val page: Int,
    val pageSize: Int,
    val totalCount: Int,
    val hasNext: Boolean
) {
    val totalPages: Int
        get() = (totalCount + pageSize - 1) / pageSize

    val hasPrevious: Boolean
        get() = page > 1

    companion object {
        fun <T> empty(page: Int = 1, pageSize: Int = 20): PaginatedResult<T> =
            PaginatedResult(
                items = emptyList(),
                page = page,
                pageSize = pageSize,
                totalCount = 0,
                hasNext = false
            )
    }
}
```

### Repository 実装

```kotlin
// data/repository/VideoRepositoryImpl.kt
class VideoRepositoryImpl : VideoRepository {

    override suspend fun findAll(page: Int, pageSize: Int): PaginatedResult<Video> = dbQuery {
        // 総件数を取得
        val totalCount = VideosTable.selectAll().count().toInt()

        // オフセットを計算
        val offset = ((page - 1) * pageSize).toLong()

        // データを取得
        val videos = VideosTable.selectAll()
            .orderBy(VideosTable.createdAt, SortOrder.DESC)
            .limit(pageSize, offset)  // limit(n, offset) の2引数形式
            .map { row -> row.toVideo() }

        PaginatedResult(
            items = videos,
            page = page,
            pageSize = pageSize,
            totalCount = totalCount,
            hasNext = (page * pageSize) < totalCount
        )
    }

    override suspend fun search(
        query: String,
        page: Int,
        pageSize: Int
    ): PaginatedResult<Video> = dbQuery {
        val searchPattern = "%${query.lowercase()}%"
        val offset = ((page - 1) * pageSize).toLong()

        val baseQuery = VideosTable.selectAll()
            .where { VideosTable.description.lowerCase() like searchPattern }

        val totalCount = baseQuery.count().toInt()

        val videos = VideosTable.selectAll()
            .where { VideosTable.description.lowerCase() like searchPattern }
            .orderBy(VideosTable.viewCount, SortOrder.DESC)
            .limit(pageSize, offset)
            .map { row -> row.toVideo() }

        PaginatedResult(
            items = videos,
            page = page,
            pageSize = pageSize,
            totalCount = totalCount,
            hasNext = (page * pageSize) < totalCount
        )
    }
}
```

### Response DTO

```kotlin
// routes/dto/response/PaginationMeta.kt
@Serializable
data class PaginationMeta(
    val page: Int,
    @SerialName("page_size")
    val pageSize: Int,
    @SerialName("total_count")
    val totalCount: Int,
    @SerialName("total_pages")
    val totalPages: Int,
    @SerialName("has_next")
    val hasNext: Boolean,
    @SerialName("has_previous")
    val hasPrevious: Boolean
) {
    companion object {
        fun <T> from(result: PaginatedResult<T>): PaginationMeta = PaginationMeta(
            page = result.page,
            pageSize = result.pageSize,
            totalCount = result.totalCount,
            totalPages = result.totalPages,
            hasNext = result.hasNext,
            hasPrevious = result.hasPrevious
        )
    }
}

// routes/dto/response/VideoListResponse.kt
@Serializable
data class VideoListResponse(
    val videos: List<VideoResponse>,
    val pagination: PaginationMeta
)
```

### Routes 実装

```kotlin
// routes/VideoRoutes.kt
fun Route.videoRoutes() {
    val getVideosUseCase by inject<GetVideosUseCase>()

    route("/api/v1/videos") {
        get {
            // クエリパラメータからページング情報を取得
            val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
            val pageSize = call.request.queryParameters["page_size"]?.toIntOrNull() ?: 20

            // バリデーション
            if (page < 1) {
                return@get call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("INVALID_PAGE", "Page must be >= 1")
                )
            }
            if (pageSize !in 1..100) {
                return@get call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("INVALID_PAGE_SIZE", "Page size must be 1-100")
                )
            }

            val result = getVideosUseCase(page, pageSize)

            call.respond(
                VideoListResponse(
                    videos = result.items.map { VideoResponse.from(it) },
                    pagination = PaginationMeta.from(result)
                )
            )
        }
    }
}
```

## カーソルベースページネーション

無限スクロールに適した方式です。

### モデル

```kotlin
// domain/model/common/CursorResult.kt
data class CursorResult<T>(
    val items: List<T>,
    val nextCursor: String?,
    val hasNext: Boolean
)
```

### Repository 実装

```kotlin
override suspend fun findAllWithCursor(
    cursor: String?,
    limit: Int
): CursorResult<Video> = dbQuery {
    val query = if (cursor != null) {
        // カーソル（作成日時）以降のデータを取得
        val cursorTime = Instant.parse(cursor)
        VideosTable.selectAll()
            .where { VideosTable.createdAt less cursorTime }
    } else {
        VideosTable.selectAll()
    }

    val videos = query
        .orderBy(VideosTable.createdAt, SortOrder.DESC)
        .limit(limit + 1)  // 次ページの存在確認用に1件多く取得
        .map { row -> row.toVideo() }

    val hasNext = videos.size > limit
    val items = if (hasNext) videos.dropLast(1) else videos
    val nextCursor = if (hasNext) items.lastOrNull()?.createdAt?.toString() else null

    CursorResult(
        items = items,
        nextCursor = nextCursor,
        hasNext = hasNext
    )
}
```

### Response DTO

```kotlin
@Serializable
data class CursorPaginationMeta(
    @SerialName("next_cursor")
    val nextCursor: String?,
    @SerialName("has_next")
    val hasNext: Boolean
)

@Serializable
data class VideoFeedResponse(
    val videos: List<VideoResponse>,
    val pagination: CursorPaginationMeta
)
```

### Routes 実装

```kotlin
get("/feed") {
    val cursor = call.request.queryParameters["cursor"]
    val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 20

    val result = getFeedUseCase(cursor, limit.coerceIn(1, 50))

    call.respond(
        VideoFeedResponse(
            videos = result.items.map { VideoResponse.from(it) },
            pagination = CursorPaginationMeta(
                nextCursor = result.nextCursor,
                hasNext = result.hasNext
            )
        )
    )
}
```

## フィルタリングとソート

### Request パラメータ

```kotlin
// routes/dto/request/VideoListParams.kt
data class VideoListParams(
    val page: Int = 1,
    val pageSize: Int = 20,
    val sortBy: String = "created_at",
    val sortOrder: String = "desc",
    val status: String? = null,
    val authorId: String? = null
) {
    fun validate(): List<String> {
        val errors = mutableListOf<String>()
        if (page < 1) errors.add("page must be >= 1")
        if (pageSize !in 1..100) errors.add("page_size must be 1-100")
        if (sortBy !in listOf("created_at", "view_count", "like_count")) {
            errors.add("sort_by must be one of: created_at, view_count, like_count")
        }
        if (sortOrder !in listOf("asc", "desc")) {
            errors.add("sort_order must be asc or desc")
        }
        return errors
    }

    companion object {
        fun from(queryParameters: Parameters): VideoListParams = VideoListParams(
            page = queryParameters["page"]?.toIntOrNull() ?: 1,
            pageSize = queryParameters["page_size"]?.toIntOrNull() ?: 20,
            sortBy = queryParameters["sort_by"] ?: "created_at",
            sortOrder = queryParameters["sort_order"] ?: "desc",
            status = queryParameters["status"],
            authorId = queryParameters["author_id"]
        )
    }
}
```

### Repository 実装

```kotlin
override suspend fun findAll(
    params: VideoListParams
): PaginatedResult<Video> = dbQuery {
    val offset = ((params.page - 1) * params.pageSize).toLong()

    // フィルタ条件を構築
    var query = VideosTable.selectAll()

    params.status?.let { status ->
        query = query.where { VideosTable.status eq status }
    }

    params.authorId?.let { authorId ->
        query = query.where { VideosTable.authorId eq authorId }
    }

    // ソート
    val sortColumn = when (params.sortBy) {
        "view_count" -> VideosTable.viewCount
        "like_count" -> VideosTable.likeCount
        else -> VideosTable.createdAt
    }
    val sortOrder = if (params.sortOrder == "asc") SortOrder.ASC else SortOrder.DESC

    val totalCount = query.count().toInt()

    val videos = query
        .orderBy(sortColumn, sortOrder)
        .limit(params.pageSize, offset)
        .map { row -> row.toVideo() }

    PaginatedResult(
        items = videos,
        page = params.page,
        pageSize = params.pageSize,
        totalCount = totalCount,
        hasNext = (params.page * params.pageSize) < totalCount
    )
}
```

## API レスポンス例

### オフセットベース

```json
{
  "videos": [
    { "id": "1", "title": "Video 1", ... },
    { "id": "2", "title": "Video 2", ... }
  ],
  "pagination": {
    "page": 1,
    "page_size": 20,
    "total_count": 150,
    "total_pages": 8,
    "has_next": true,
    "has_previous": false
  }
}
```

### カーソルベース

```json
{
  "videos": [
    { "id": "1", "title": "Video 1", ... },
    { "id": "2", "title": "Video 2", ... }
  ],
  "pagination": {
    "next_cursor": "2024-01-15T10:30:00Z",
    "has_next": true
  }
}
```

## ベストプラクティス

1. **デフォルト値を設定**: `page=1`, `pageSize=20`
2. **最大ページサイズを制限**: `pageSize <= 100`
3. **総件数は必要な場合のみ取得**: パフォーマンス考慮
4. **インデックスを活用**: ソート・フィルタ対象カラムにインデックス
5. **一貫したレスポンス形式**: 全エンドポイントで同じ形式を使用
