package com.appmaster.data.repository

import com.appmaster.data.dao.BestDao
import com.appmaster.domain.model.entity.Best
import com.appmaster.domain.model.valueobject.BestId
import com.appmaster.domain.model.valueobject.ThemeId
import com.appmaster.domain.model.valueobject.UserId
import com.appmaster.domain.repository.BestRepository

class BestRepositoryImpl(
    private val dao: BestDao
) : BestRepository {

    override suspend fun findById(id: BestId): Best? = dao.findById(id)

    override suspend fun findByThemeId(themeId: ThemeId, limit: Int, offset: Int): List<Best> =
        dao.findByThemeId(themeId, limit, offset)

    override suspend fun findByAuthorId(authorId: UserId, limit: Int, offset: Int): List<Best> =
        dao.findByAuthorId(authorId, limit, offset)

    override suspend fun findByAuthorAndTheme(authorId: UserId, themeId: ThemeId): Best? =
        dao.findByAuthorAndTheme(authorId, themeId)

    override suspend fun save(best: Best): Best = dao.insert(best)
}
