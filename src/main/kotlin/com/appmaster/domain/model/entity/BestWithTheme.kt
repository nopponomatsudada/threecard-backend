package com.appmaster.domain.model.entity

/**
 * Best エンティティに、Theme のメタ情報（タイトル / タグ名）を付与した read-only 集約。
 *
 * `/users/me/bests` のような自分の投稿一覧で、クライアントが Theme 情報を別途取得しなくて済むよう
 * Repository 層で JOIN して返すために使用する。
 */
data class BestWithTheme(
    val best: Best,
    val themeTitle: String,
    val tagId: String,
)
