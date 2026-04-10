package com.appmaster.domain.model.valueobject

import com.appmaster.domain.error.DomainError
import com.appmaster.domain.error.DomainException
import java.util.UUID

@JvmInline
value class CollectionId(val value: String) {
    init {
        require(value)
    }

    companion object {
        fun generate(): CollectionId = CollectionId(UUID.randomUUID().toString())

        private fun require(value: String) {
            try {
                UUID.fromString(value)
            } catch (e: IllegalArgumentException) {
                throw DomainException(DomainError.ValidationError("不正なコレクションIDです"))
            }
        }
    }
}
