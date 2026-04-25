package com.appmaster.domain.model.`enum`

enum class ModerationTargetType(val id: String) {
    THEME("theme"),
    BEST("best");

    companion object {
        fun fromId(id: String): ModerationTargetType? = entries.find { it.id == id }
    }
}
