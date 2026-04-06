package com.appmaster.domain.model.valueobject

import java.util.UUID

@JvmInline
value class ThemeId(val value: String) {
    companion object {
        fun generate(): ThemeId = ThemeId(UUID.randomUUID().toString())
    }
}
