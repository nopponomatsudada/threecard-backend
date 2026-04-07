package com.appmaster.domain.model.valueobject

/**
 * Clamps page-list parameters to safe values. Use case layer should
 * always wrap raw client input with `Pagination.of(...)`.
 */
data class Pagination(val limit: Int, val offset: Int) {
    companion object {
        const val DEFAULT_LIMIT = 20
        const val MAX_LIMIT = 50

        fun of(limit: Int? = null, offset: Int? = null): Pagination = Pagination(
            limit = (limit ?: DEFAULT_LIMIT).coerceIn(1, MAX_LIMIT),
            offset = maxOf(0, offset ?: 0)
        )
    }
}
