package com.appmaster.domain.model.`enum`

enum class ModerationStatus(val id: String) {
    PENDING("pending"),
    APPROVED("approved"),
    REJECTED("rejected"),
    REPORTED("reported");

    val isReviewDecision: Boolean get() = this == APPROVED || this == REJECTED

    companion object {
        fun fromId(id: String): ModerationStatus? = entries.find { it.id == id }
    }
}
