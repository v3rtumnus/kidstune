package at.kidstune.kids.data.repository

import androidx.room.withTransaction
import at.kidstune.kids.data.local.KidstuneDatabase
import at.kidstune.kids.data.local.entities.LocalAlbum
import at.kidstune.kids.data.local.entities.LocalContentEntry
import at.kidstune.kids.data.local.entities.LocalFavorite
import at.kidstune.kids.data.local.entities.LocalTrack
import at.kidstune.kids.data.remote.KidstuneApiClient
import at.kidstune.kids.domain.model.ContentScope
import at.kidstune.kids.domain.model.ContentType
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Orchestrates a full sync: fetches the complete content tree from the backend
 * and atomically replaces the Room database contents for the given profile.
 *
 * On network failure the database is left untouched so cached data remains
 * available to the UI.
 */
@Singleton
class SyncRepository @Inject constructor(
    private val apiClient: KidstuneApiClient,
    private val db: KidstuneDatabase,
    private val favoriteRepository: FavoriteRepository
) {

    /**
     * Performs a full sync for [profileId].
     *
     * @return [Result.success] on success, [Result.failure] if the network call
     *         or database write fails. The UI should treat failure as "show cached
     *         data" and not surface an error to the child.
     */
    suspend fun fullSync(profileId: String): Result<Unit> = runCatching {
        val payload = apiClient.fetchFullSync(profileId)

        db.withTransaction {
            // ── 1. Replace content entries ────────────────────────────────
            db.contentDao().deleteAll(profileId)

            val syncedAt = Instant.now()
            val contentEntries = payload.content.map { dto ->
                LocalContentEntry(
                    id           = dto.id,
                    profileId    = profileId,
                    spotifyUri   = dto.spotifyUri,
                    scope        = ContentScope.valueOf(dto.scope),
                    contentType  = ContentType.valueOf(dto.contentType),
                    title        = dto.title,
                    imageUrl     = dto.imageUrl,
                    artistName   = dto.artistName,
                    lastSyncedAt = syncedAt
                )
            }
            db.contentDao().insertAll(contentEntries)

            // ── 2. Build albums + tracks from nested DTOs ─────────────────
            val albums = mutableListOf<LocalAlbum>()
            val tracks = mutableListOf<LocalTrack>()

            payload.content.forEach { entryDto ->
                entryDto.albums.forEach { albumDto ->
                    // Stable Room ID: content-entry-id + album URI (handles the case
                    // where the same Spotify album appears under multiple entries).
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
            }

            db.albumDao().insertAll(albums)
            db.trackDao().insertAll(tracks)

            // ── 3. Replace synced favorites; preserve locally-queued ones ─
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

            // Re-insert locally-queued favorites that the backend doesn't know about yet
            unsynced.filter { it.spotifyTrackUri !in syncedUris }.forEach { fav ->
                db.favoriteDao().insert(fav)
            }

            // ── 4. Persist profile name for display ───────────────────────
            // (Profile preferences are updated by the caller after this returns.)
        }

        // ── 5. Upload unsynced favorites to backend ───────────────────────
        // Runs outside the transaction (network I/O).
        favoriteRepository.uploadUnsynced(profileId)
    }
}
