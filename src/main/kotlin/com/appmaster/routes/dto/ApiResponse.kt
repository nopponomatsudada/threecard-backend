package com.appmaster.routes.dto

import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable

@Serializable
data class ApiResponse<T>(
    val data: T,
    val meta: Meta = Meta()
)

@Serializable
data class Meta(
    val timestamp: String = Clock.System.now().toString()
)
