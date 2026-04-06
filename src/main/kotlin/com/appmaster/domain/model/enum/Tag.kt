package com.appmaster.domain.model.`enum`

enum class Tag(val id: String, val label: String) {
    MUSIC("music", "音楽"),
    LIFESTYLE("lifestyle", "暮らし"),
    BOOKS("books", "本"),
    FASHION("fashion", "ファッション"),
    FOOD("food", "食"),
    MOVIES("movies", "映画"),
    TRAVEL("travel", "旅"),
    GADGETS("gadgets", "ガジェット");

    companion object {
        fun fromId(id: String): Tag? = entries.find { it.id == id }
    }
}
