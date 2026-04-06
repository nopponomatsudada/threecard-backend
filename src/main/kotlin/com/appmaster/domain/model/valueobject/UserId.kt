package com.appmaster.domain.model.valueobject

import java.util.UUID

@JvmInline
value class UserId(val value: String) {
    companion object {
        fun generate(): UserId = UserId(UUID.randomUUID().toString())
    }
}
