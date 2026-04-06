package com.appmaster.domain.model.`enum`

enum class Rank(val value: Int) {
    FIRST(1),
    SECOND(2),
    THIRD(3);

    companion object {
        fun fromValue(value: Int): Rank? = entries.find { it.value == value }
    }
}
