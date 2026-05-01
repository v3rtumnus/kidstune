package at.kidstune.kids.data.repository

import at.kidstune.kids.data.local.ContentDao
import at.kidstune.kids.data.local.entities.LocalContentEntry
import at.kidstune.kids.domain.model.ContentScope
import at.kidstune.kids.domain.model.ContentType
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for [LocalContentEntry] data.
 * All reads go through Room; writes happen only via [SyncRepository].
 */
@Singleton
class ContentRepository @Inject constructor(
    private val contentDao: ContentDao
) {

    fun getAll(profileId: String): Flow<List<LocalContentEntry>> =
        contentDao.getAll(profileId)

    fun getByType(profileId: String, type: ContentType): Flow<List<LocalContentEntry>> =
        contentDao.getByType(profileId, type)

    fun getMusicOrdered(profileId: String): Flow<List<LocalContentEntry>> =
        contentDao.getByTypeOrderedByLastListened(profileId, ContentType.MUSIC)

    fun getMusicByScope(profileId: String, scope: ContentScope): Flow<List<LocalContentEntry>> =
        contentDao.getByTypeAndScopeOrdered(profileId, ContentType.MUSIC, scope)

    suspend fun getById(id: String): LocalContentEntry? =
        contentDao.getById(id)
}
