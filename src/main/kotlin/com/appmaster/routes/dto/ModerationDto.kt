package com.appmaster.routes.dto

import kotlinx.serialization.Serializable

@Serializable
data class UpdateModerationStatusRequest(
    val status: String
)
