package com.appmaster.domain.model.entity

/**
 * Aggregate of a user's profile and headline counts.
 * Returned by `GetMyProfileUseCase`.
 */
data class ProfileWithStats(
    val user: User,
    val bestCount: Int,
    val bookmarkCount: Int
)
