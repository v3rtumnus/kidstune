package at.kidstune.kids.sync

import at.kidstune.kids.data.repository.DiscoverRepository
import at.kidstune.kids.data.repository.FavoriteRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Drains locally-queued writes to the backend.
 *
 * Currently handles:
 *  - Favorites with synced=false (backed by [local_favorite] table)
 *  - Content requests with status=PENDING_UPLOAD (backed by [local_content_request] table)
 *
 * Each [drain] call attempts to upload every pending item; individual failures
 * are swallowed so a single bad item never blocks the rest of the queue.
 */
@Singleton
class OfflineQueue @Inject constructor(
    private val favoriteRepository: FavoriteRepository,
    private val discoverRepository: DiscoverRepository,
) {

    /**
     * Uploads all unsynced items for [profileId] and marks them synced on success.
     * Safe to call repeatedly – idempotent if the queue is already empty.
     */
    suspend fun drain(profileId: String) {
        favoriteRepository.uploadUnsynced(profileId)
        discoverRepository.drainOfflineRequests(profileId)
    }
}
