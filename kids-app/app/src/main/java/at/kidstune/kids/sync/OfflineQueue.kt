package at.kidstune.kids.sync

import at.kidstune.kids.data.repository.FavoriteRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Drains locally-queued writes (favorites with synced=false) to the backend.
 *
 * The queue is backed by the Room [local_favorite] table using the `synced`
 * boolean flag – no separate queue table is needed. Each [drain] call attempts
 * to upload every pending item; individual failures are swallowed so a single
 * bad item never blocks the rest of the queue.
 *
 * Future content requests will be added here when Phase 7 lands.
 */
@Singleton
class OfflineQueue @Inject constructor(
    private val favoriteRepository: FavoriteRepository
) {

    /**
     * Uploads all unsynced items for [profileId] and marks them synced on success.
     * Safe to call repeatedly – idempotent if the queue is already empty.
     */
    suspend fun drain(profileId: String) {
        favoriteRepository.uploadUnsynced(profileId)
    }
}
