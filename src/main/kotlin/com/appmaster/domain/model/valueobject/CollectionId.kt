package com.appmaster.domain.model.valueobject

import java.util.UUID

@JvmInline
value class CollectionId(val value: String) {
    companion object {
        fun generate(): CollectionId = CollectionId(UUID.randomUUID().toString())
    }
}
