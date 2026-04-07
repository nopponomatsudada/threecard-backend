package com.appmaster.domain.model.entity

/**
 * A collection paired with its current card count.
 * Returned by `GetCollectionsUseCase` so the routes layer doesn't carry a tuple.
 */
data class CollectionWithCount(
    val collection: Collection,
    val cardCount: Int
)
