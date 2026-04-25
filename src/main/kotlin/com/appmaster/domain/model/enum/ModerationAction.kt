package com.appmaster.domain.model.`enum`

enum class ModerationAction(val id: String) {
    APPROVE("approve"),
    REJECT("reject"),
    SKIP("skip");

    companion object {
        fun fromId(id: String): ModerationAction? = entries.find { it.id == id }
    }
}
