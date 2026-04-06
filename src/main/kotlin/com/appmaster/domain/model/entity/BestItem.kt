package com.appmaster.domain.model.entity

import com.appmaster.domain.model.`enum`.Rank
import com.appmaster.domain.model.valueobject.BestId

data class BestItem(
    val id: String,
    val bestId: BestId,
    val rank: Rank,
    val name: String,
    val description: String?
)
