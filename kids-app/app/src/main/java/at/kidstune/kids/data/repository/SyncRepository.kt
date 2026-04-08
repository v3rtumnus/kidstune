package at.kidstune.kids.data.repository

import android.database.sqlite.SQLiteFullException
import androidx.room.withTransaction
import at.kidstune.kids.data.local.KidstuneDatabase
import at.kidstune.kids.data.local.entities.LocalAlbum
import at.kidstune.kids.data.local.entities.LocalContentEntry
import at.kidstune.kids.data.local.entities.LocalFavorite
import at.kidstune.kids.data.local.entities.LocalTrack
import at.kidstune.kids.data.remote.KidstuneApiClient
import at.kidstune.kids.data.remote.dto.SyncContentEntryDto
import at.kidstune.kids.domain.model.ContentScope
import at.kidstune.kids.domain.model.ContentType
import java.io.IOException
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Orchestrates full and delta syncs between the backend and the local Room database.
 *
 * Both methods return [Result.success]/[Result.failure]. On failure the database is
 * left untouched so the UI continues to show cached data.
 *
 * [storageFullError] is set to `true` when a sync fails because the device has no free
 * storage space (SQLiteFullException or OS-level IOException with a disk-full message).
 * It stays `true` until the next successful sync, signalling to the UI that a
 * [StorageFullScreen] should be shown.
 */
@Singleton
class SyncRepository @Inject constructor(
    private val apiClient: KidstuneApiClient,
    private val db: KidstuneDatabase,
    private val favoriteRepository: FavoriteRepository
) {

    private val _storageFullError = MutableStateFlow(false)
    val storageFullError: StateFlow<Boolean> = _storageFullError.asStateFlow()

    // ─────────────────────────────────────────────────────────────────────────
    // Delta sync
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Applies a delta payload for [profileId] using [since] as the query timestamp.
     *
     * Conflict resolution:
     * - Content: server wins (added/updated/removed applied unconditionally)
     * - Favorites: server wins for deletions ([favoritesRemoved] deletes even locally
     *   unsynced entries); local additions are uploaded after the transaction.
     */
    suspend fun deltaSync(profileId: String, since: String): Result<Unit> = runCatching {
        val payload = apiClient.fetchDeltaSync(profileId, since)

        db.withTransaction {
            val syncedAt = Instant.now()

            // ── 1. Removed entries (FK cascade deletes albums + tracks) ───────
            payload.removed.forEach { entryId -> db.contentDao().deleteById(entryId) }

            // ── 2. Updated entries – replace albums/tracks, upsert entry ─��────
            payload.updated.forEach { dto ->
                db.albumDao().deleteByContentEntryId(dto.id)   // cascades to tracks
                db.contentDao().insertAll(listOf(dto.toLocalEntry(profileId, syncedAt)))
                insertAlbumsAndTracks(dto, syncedAt)
            }

            // ── 3. Added entries ───────────────────────────────────────────────
            payload.added.forEach { dto ->
                db.contentDao().insertAll(listOf(dto.toLocalEntry(profileId, syncedAt)))
                insertAlbumsAndTracks(dto, syncedAt)
            }

            // ── 4. Favorites added by server ───────────────────────────────────
            payload.favoritesAdded.forEach { dto ->
                db.favoriteDao().insert(
                    LocalFavorite(
                        id              = "fav__${profileId}__${dto.spotifyTrackUri}",
                        profileId       = profileId,
                        spotifyTrackUri = dto.spotifyTrackUri,
                        title           = dto.trackTitle,
                        artistName      = dto.artistName,
                        imageUrl        = dto.trackImageUrl,
                        addedAt         = if (dto.addedAt != null) Instant.parse(dto.addedAt)
                                          else syncedAt,
                        synced          = true
                    )
                )
            }

            // ── 5. Favorites removed by server (server wins for deletions) ─────
            payload.favoritesRemoved.forEach { trackUri ->
                db.favoriteDao().deleteByUri(profileId, trackUri)
            }
        }

        // Upload locally-queued favorites (runs outside the transaction)
        favoriteRepository.uploadUnsynced(profileId)
    }.also { result ->
        result.onSuccess  { _storageFullError.value = false }
        result.onFailure  { t -> if (t.isStorageFull()) _storageFullError.value = true }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Full sync
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Replaces the entire content tree for [profileId] with the server's response.
     * Locally-unsynced favorites are preserved and re-queued.
     */
    suspend fun fullSync(profileId: String): Result<Unit> = runCatching {
        val payload = apiClient.fetchFullSync(profileId)

        db.withTransaction {
            // ── 1. Replace content entries ─────────────────────────────────────
            db.contentDao().deleteAll(profileId)

            val syncedAt = Instant.now()
            db.contentDao().insertAll(payload.content.map { it.toLocalEntry(profileId, syncedAt) })

            // ── 2. Build albums + tracks ───────────────────────────────────────
            payload.content.forEach { entryDto -> insertAlbumsAndTracks(entryDto, syncedAt) }

            // ── 3. Replace synced favorites; preserve locally-queued ones ──────
            val unsynced = db.favoriteDao().getUnsynced(profileId)
            db.favoriteDao().deleteAllSynced(profileId)

            val syncedUris = payload.favorites.map { it.spotifyTrackUri }.toSet()
            payload.favorites.forEach { dto ->
                db.favoriteDao().insert(
                    LocalFavorite(
                        id              = "fav__${profileId}__${dto.spotifyTrackUri}",
                        profileId       = profileId,
                        spotifyTrackUri = dto.spotifyTrackUri,
                        title           = dto.trackTitle,
                        artistName      = dto.artistName,
                        imageUrl        = dto.trackImageUrl,
                        addedAt         = if (dto.addedAt != null) Instant.parse(dto.addedAt)
                                          else syncedAt,
                        synced          = true
                    )
                )
            }
            // Re-insert locally-queued favorites the backend doesn't know about yet
            unsynced.filter { it.spotifyTrackUri !in syncedUris }.forEach { fav ->
                db.favoriteDao().insert(fav)
            }
        }

        // Upload unsynced favorites (runs outside the transaction)
        favoriteRepository.uploadUnsynced(profileId)
    }.also { result ->
        result.onSuccess  { _storageFullError.value = false }
        result.onFailure  { t -> if (t.isStorageFull()) _storageFullError.value = true }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Shared helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns `true` when the throwable (or any chained cause) indicates a
     * disk-full / storage-exhausted condition at the SQLite or OS level.
     */
    private fun Throwable.isStorageFull(): Boolean =
        generateSequence(this) { it.cause }.any { t ->
            t is SQLiteFullException ||
            (t is IOException && ("no space" in (t.message?.lowercase() ?: "") ||
                                  "disk full" in (t.message?.lowercase() ?: "")))
        }

    private fun SyncContentEntryDto.toLocalEntry(
        profileId: String,
        syncedAt: Instant
    ) = LocalContentEntry(
        id           = id,
        profileId    = profileId,
        spotifyUri   = spotifyUri,
        scope        = ContentScope.valueOf(scope),
        contentType  = ContentType.valueOf(contentType),
        title        = title,
        imageUrl     = imageUrl,
        artistName   = artistName,
        lastSyncedAt = syncedAt
    )

    private suspend fun insertAlbumsAndTracks(
        entryDto: SyncContentEntryDto,
        syncedAt: Instant
    ) {
        val albums = mutableListOf<LocalAlbum>()
        val tracks = mutableListOf<LocalTrack>()

        entryDto.albums.forEach { albumDto ->
            val albumId = "${entryDto.id}__${albumDto.spotifyAlbumUri}"
            albums += LocalAlbum(
                id              = albumId,
                contentEntryId  = entryDto.id,
                spotifyAlbumUri = albumDto.spotifyAlbumUri,
                title           = albumDto.title,
                imageUrl        = albumDto.imageUrl,
                releaseDate     = albumDto.releaseDate,
                totalTracks     = albumDto.tracks.size,
                contentType     = ContentType.valueOf(albumDto.contentType)
            )
            albumDto.tracks.forEach { trackDto ->
                tracks += LocalTrack(
                    id              = "${albumId}__${trackDto.spotifyTrackUri}",
                    albumId         = albumId,
                    spotifyTrackUri = trackDto.spotifyTrackUri,
                    title           = trackDto.title,
                    artistName      = trackDto.artistName,
                    durationMs      = trackDto.durationMs ?: 0L,
                    trackNumber     = trackDto.trackNumber ?: 1,
                    discNumber      = trackDto.discNumber ?: 1,
                    imageUrl        = trackDto.imageUrl
                )
            }
        }

        db.albumDao().insertAll(albums)
        db.trackDao().insertAll(tracks)
    }
}
