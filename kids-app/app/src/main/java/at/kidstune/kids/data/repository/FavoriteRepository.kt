package at.kidstune.kids.data.repository

import android.util.Log
import at.kidstune.kids.data.local.FavoriteDao
import at.kidstune.kids.data.local.entities.LocalFavorite
import at.kidstune.kids.data.local.entities.LocalTrack
import at.kidstune.kids.data.preferences.ProfilePreferences
import at.kidstune.kids.data.remote.KidstuneApiClient
import at.kidstune.kids.data.remote.dto.AddFavoriteRequestDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "FavoriteRepository"

/**
 * Single source of truth for favorites.
 *
 * Writes go to Room immediately (optimistic / offline-first) with [synced]=false.
 * The backend upload is triggered by [uploadUnsynced], which [SyncRepository] calls
 * during a full sync.
 */
@Singleton
class FavoriteRepository @Inject constructor(
    private val favoriteDao: FavoriteDao,
    private val apiClient: KidstuneApiClient,
    private val prefs: ProfilePreferences
) {

    /**
     * Toggles the favorite status of [track]:
     * - If already a favorite → remove from Room (will be deleted from backend on next sync)
     * - If not a favorite → insert into Room with synced=false (uploaded on next sync)
     */
    suspend fun toggleFavorite(track: LocalTrack) {
        val profileId = prefs.boundProfileId ?: return
        val alreadyFavorite = favoriteDao.existsByTrackUri(profileId, track.spotifyTrackUri).first()
        if (alreadyFavorite) {
            favoriteDao.deleteByUri(profileId, track.spotifyTrackUri)
        } else {
            favoriteDao.insert(
                LocalFavorite(
                    id              = LocalFavorite.idFor(profileId, track.spotifyTrackUri),
                    profileId       = profileId,
                    spotifyTrackUri = track.spotifyTrackUri,
                    title           = track.title,
                    artistName      = track.artistName,
                    imageUrl        = track.imageUrl,
                    addedAt         = Instant.now(),
                    synced          = false
                )
            )
        }
    }

    /**
     * Returns a [Flow<Boolean>] that emits true when [trackUri] is in the favorites
     * for the currently bound profile.
     */
    fun isFavorite(trackUri: String): Flow<Boolean> {
        val profileId = prefs.boundProfileId ?: return flowOf(false)
        return favoriteDao.existsByTrackUri(profileId, trackUri)
    }

    /** All favorites for the current profile, ordered by addedAt desc. */
    fun getAllFavorites(): Flow<List<LocalFavorite>> {
        val profileId = prefs.boundProfileId ?: return flowOf(emptyList())
        return favoriteDao.getAll(profileId)
    }

    /**
     * Uploads all unsynced favorites to the backend and marks them synced.
     * Individual upload failures are logged and skipped (offline-first contract).
     */
    suspend fun uploadUnsynced(profileId: String) {
        val unsynced = favoriteDao.getUnsynced(profileId)
        for (fav in unsynced) {
            try {
                apiClient.addFavorite(
                    profileId,
                    AddFavoriteRequestDto(
                        spotifyTrackUri = fav.spotifyTrackUri,
                        trackTitle      = fav.title,
                        trackImageUrl   = fav.imageUrl,
                        artistName      = fav.artistName
                    )
                )
                favoriteDao.markSynced(fav.id)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to upload favorite ${fav.spotifyTrackUri}: ${e.message}")
            }
        }
    }

    /**
     * Notifies the backend of locally-removed favorites.
     * Individual failures are logged and skipped.
     */
    suspend fun deleteFromBackend(profileId: String, trackUris: List<String>) {
        for (uri in trackUris) {
            try {
                apiClient.deleteFavorite(profileId, uri)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to delete favorite $uri from backend: ${e.message}")
            }
        }
    }
}
