@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.appmaster.routes.dto

import com.appmaster.domain.model.entity.Collection
import com.appmaster.domain.model.entity.CollectionCard
import kotlinx.serialization.Serializable

@Serializable
data class CollectionResponse(
    val id: String,
    val title: String,
    val cardCount: Int,
    val createdAt: String
)

@Serializable
data class CreateCollectionRequest(
    val title: String
)

@Serializable
data class AddCardRequest(
    val bestId: String
)

@Serializable
data class CollectionCardResponse(
    val id: String,
    val collectionId: String,
    val bestId: String
)

fun Collection.toDto(cardCount: Int = 0) = CollectionResponse(
    id = id.value,
    title = title,
    cardCount = cardCount,
    createdAt = createdAt.toString()
)

fun CollectionCard.toDto() = CollectionCardResponse(
    id = id,
    collectionId = collectionId.value,
    bestId = bestId.value
)
