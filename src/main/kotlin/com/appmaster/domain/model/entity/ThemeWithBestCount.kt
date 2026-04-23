package com.appmaster.domain.model.entity

/**
 * A theme paired with its approved best count.
 * Returned by `GetThemesUseCase` / `GetThemeDetailUseCase` so the routes layer doesn't carry a tuple.
 */
data class ThemeWithBestCount(
    val theme: Theme,
    val bestCount: Int
)
