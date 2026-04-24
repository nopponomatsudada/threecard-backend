package com.appmaster.routes

import com.appmaster.domain.usecase.bookmark.AddBookmarkUseCase
import com.appmaster.domain.usecase.bookmark.CheckBookmarksUseCase
import com.appmaster.domain.usecase.bookmark.GetBookmarksUseCase
import com.appmaster.domain.usecase.bookmark.RemoveBookmarkUseCase
import com.appmaster.routes.dto.AddBookmarkRequest
import com.appmaster.routes.dto.ApiResponse
import com.appmaster.routes.dto.toDto
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

fun Route.bookmarkRoutes() {
    val addBookmarkUseCase by inject<AddBookmarkUseCase>()
    val removeBookmarkUseCase by inject<RemoveBookmarkUseCase>()
    val getBookmarksUseCase by inject<GetBookmarksUseCase>()
    val checkBookmarksUseCase by inject<CheckBookmarksUseCase>()

    authenticate("jwt") {
        rateLimit(RateLimitName("api")) {
        route("/api/v1/bookmarks") {

            post {
                val userId = call.requireUserId()
                val request = call.receive<AddBookmarkRequest>()
                val bookmark = addBookmarkUseCase(
                    AddBookmarkUseCase.Params(userId = userId, bestItemId = request.bestItemId)
                )
                call.respond(HttpStatusCode.Created, ApiResponse(data = bookmark.toDto()))
            }

            delete("/{bestItemId}") {
                val userId = call.requireUserId()
                val bestItemId = call.parameters["bestItemId"]!!
                removeBookmarkUseCase(
                    RemoveBookmarkUseCase.Params(userId = userId, bestItemId = bestItemId)
                )
                call.respond(HttpStatusCode.NoContent)
            }

            get {
                val userId = call.requireUserId()
                val pagination = call.parsePagination()
                val items = getBookmarksUseCase(
                    GetBookmarksUseCase.Params(
                        userId = userId,
                        limit = pagination.limit,
                        offset = pagination.offset
                    )
                )
                call.respond(ApiResponse(data = items.map { it.toDto() }))
            }

            get("/check") {
                val userId = call.requireUserId()
                val bestItemIdsParam = call.request.queryParameters["bestItemIds"]
                val bestItemIds = bestItemIdsParam?.split(",")?.filter { it.isNotBlank() }?.take(100) ?: emptyList()
                val bookmarkedIds = checkBookmarksUseCase(
                    CheckBookmarksUseCase.Params(userId = userId, bestItemIds = bestItemIds)
                )
                call.respond(ApiResponse(data = bookmarkedIds.toList()))
            }
        }
        }
    }
}
