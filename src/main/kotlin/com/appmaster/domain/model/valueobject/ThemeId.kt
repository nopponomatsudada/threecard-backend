package com.appmaster.domain.model.valueobject

import com.appmaster.domain.error.DomainError
import com.appmaster.domain.error.DomainException
import java.util.UUID

@JvmInline
value class ThemeId(val value: String) {
    init {
        require(value)
    }

    companion object {
        fun generate(): ThemeId = ThemeId(UUID.randomUUID().toString())

        // Path-param-facing IDs reject non-UUID input at the value-object boundary
        // so the route layer maps malformed IDs to 400 (DomainException) rather than
        // letting them surface as PSQLException 500 from the JDBC driver.
        private fun require(value: String) {
            try {
                UUID.fromString(value)
            } catch (e: IllegalArgumentException) {
                throw DomainException(DomainError.ValidationError("不正なテーマIDです"))
            }
        }
    }
}
