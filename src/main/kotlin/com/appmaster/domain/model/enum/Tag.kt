package com.appmaster.domain.model.`enum`

enum class Tag(val id: String, val label: String, val color: String) {
    MUSIC("music", "音楽", "#E85D75"),
    LIFESTYLE("lifestyle", "暮らし", "#F5A623"),
    BOOKS("books", "本", "#4A90D9"),
    FASHION("fashion", "ファッション", "#BD6BD9"),
    FOOD("food", "食", "#F5823A"),
    MOVIES("movies", "映画", "#50C7B7"),
    TRAVEL("travel", "旅", "#5BBD72"),
    GADGETS("gadgets", "ガジェット", "#7B8A9E");

    companion object {
        fun fromId(id: String): Tag? = entries.find { it.id == id }
    }
}
