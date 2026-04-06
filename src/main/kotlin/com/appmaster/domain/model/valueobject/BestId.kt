package com.appmaster.domain.model.valueobject

import java.util.UUID

@JvmInline
value class BestId(val value: String) {
    companion object {
        fun generate(): BestId = BestId(UUID.randomUUID().toString())
    }
}
