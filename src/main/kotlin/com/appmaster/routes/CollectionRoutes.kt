package com.appmaster.routes

import com.appmaster.domain.model.valueobject.BestId
import com.appmaster.domain.model.valueobject.CollectionId
import com.appmaster.domain.usecase.collection.AddCardToCollectionUseCase
import com.appmaster.domain.usecase.collection.CreateCollectionUseCase
import com.appmaster.domain.usecase.collection.DeleteCollectionUseCase
import com.appmaster.domain.usecase.collection.GetCollectionCardsUseCase
import com.appmaster.domain.usecase.collection.GetCollectionsUseCase
import com.appmaster.domain.usecase.collection.RemoveCardFromCollectionUseCase
import com.appmaster.routes.dto.AddCardRequest
import com.appmaster.routes.dto.ApiResponse
import com.appmaster.routes.dto.CreateCollectionRequest
import com.appmaster.routes.dto.toDto
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

fun Route.collectionRoutes() {
    val getCollectionsUseCase by inject<GetCollectionsUseCase>()
    val createCollectionUseCase by inject<CreateCollectionUseCase>()
    val deleteCollectionUseCase by inject<DeleteCollectionUseCase>()
    val getCollectionCardsUseCase by inject<GetCollectionCardsUseCase>()
    val addCardToCollectionUseCase by inject<AddCardToCollectionUseCase>()
    val removeCardFromCollectionUseCase by inject<RemoveCardFromCollectionUseCase>()

    authenticate("jwt") {
        rateLimit(RateLimitName("api")) {
        route("/api/v1/collections") {

            get {
                val userId = call.requireUserId()
                val collectionsWithCounts = getCollectionsUseCase(userId)
                call.respond(ApiResponse(data = collectionsWithCounts.map { item ->
                    item.collection.toDto(item.cardCount)
                }))
            }

            post {
                val userId = call.requireUserId()
                val request = call.receive<CreateCollectionRequest>()
                val collection = createCollectionUseCase(
                    CreateCollectionUseCase.Params(userId = userId, title = request.title)
                )
                call.respond(HttpStatusCode.Created, ApiResponse(data = collection.toDto()))
            }

            route("/{collectionId}") {

                delete {
                    val userId = call.requireUserId()
                    val collectionId = CollectionId(call.parameters["collectionId"]!!)
                    deleteCollectionUseCase(
                        DeleteCollectionUseCase.Params(collectionId = collectionId, userId = userId)
                    )
                    call.respond(HttpStatusCode.NoContent)
                }

                route("/cards") {

                    get {
                        val userId = call.requireUserId()
                        val collectionId = CollectionId(call.parameters["collectionId"]!!)
                        val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 20
                        val offset = call.request.queryParameters["offset"]?.toIntOrNull() ?: 0

                        val cards = getCollectionCardsUseCase(
                            GetCollectionCardsUseCase.Params(
                                collectionId = collectionId,
                                userId = userId,
                                limit = limit,
                                offset = offset
                            )
                        )
                        call.respond(ApiResponse(data = cards.map { it.toDto() }))
                    }

                    post {
                        val userId = call.requireUserId()
                        val collectionId = CollectionId(call.parameters["collectionId"]!!)
                        val request = call.receive<AddCardRequest>()

                        val collectionCard = addCardToCollectionUseCase(
                            AddCardToCollectionUseCase.Params(
                                collectionId = collectionId,
                                bestId = BestId(request.bestId),
                                userId = userId
                            )
                        )
                        call.respond(HttpStatusCode.Created, ApiResponse(data = collectionCard.toDto()))
                    }

                    delete("/{bestId}") {
                        val userId = call.requireUserId()
                        val collectionId = CollectionId(call.parameters["collectionId"]!!)
                        val bestId = BestId(call.parameters["bestId"]!!)

                        removeCardFromCollectionUseCase(
                            RemoveCardFromCollectionUseCase.Params(
                                collectionId = collectionId,
                                bestId = bestId,
                                userId = userId
                            )
                        )
                        call.respond(HttpStatusCode.NoContent)
                    }
                }
            }
        }
        }
    }
}
