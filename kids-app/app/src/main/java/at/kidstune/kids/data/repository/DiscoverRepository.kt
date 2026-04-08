package at.kidstune.kids.data.repository

import at.kidstune.kids.data.local.ContentDao
import at.kidstune.kids.data.local.ContentRequestDao
import at.kidstune.kids.data.local.entities.LocalContentRequest
import at.kidstune.kids.data.remote.KidstuneApiClient
import at.kidstune.kids.data.remote.dto.ContentRequestResponseDto
import at.kidstune.kids.data.remote.dto.CreateContentRequestDto
import at.kidstune.kids.data.remote.dto.DiscoverItemDto
import at.kidstune.kids.domain.model.ContentScope
import at.kidstune.kids.domain.model.ContentType
import at.kidstune.kids.domain.model.DiscoverTile
import at.kidstune.kids.domain.model.PendingRequest
import at.kidstune.kids.domain.model.RequestStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provides Discover screen data:
 *  - Personalised suggestions (backend: approved content + favourites of this profile)
 *  - Spotify search results
 *  - Content request creation with offline queue support
 *  - Reactive stream of the profile's current requests
 */
@Singleton
class DiscoverRepository @Inject constructor(
    private val apiClient: KidstuneApiClient,
    private val contentDao: ContentDao,
    private val requestDao: ContentRequestDao,
) {

    // ── Suggestions ────────────────────────────────────────────────────────────

    /**
     * Fetches personalised suggestions and applies the already-approved filter.
     * Returns an empty list on network error (safe to call when offline).
     */
    suspend fun fetchSuggestions(profileId: String): List<DiscoverTile> = runCatching {
        val approvedUris = contentDao.getExistingUris(profileId).toSet()
        apiClient.fetchSuggestions(profileId)
            .filter { it.spotifyUri !in approvedUris }
            .map { it.toDiscoverTile() }
    }.getOrDefault(emptyList())

    // ── Search ─────────────────────────────────────────────────────────────────

    /**
     * Searches Spotify and applies the already-approved filter.
     * Returns an empty list on network error.
     */
    suspend fun search(profileId: String, q: String, limit: Int = 10): List<DiscoverTile> = runCatching {
        val approvedUris = contentDao.getExistingUris(profileId).toSet()
        val results = apiClient.searchContent(q, limit)

        // Flatten all result groups into a single tile list, preserving result order:
        // artists first, then albums, then playlists
        val tiles = mutableListOf<DiscoverTile>()
        results.artists.forEach  { tiles += it.toDiscoverTile(ContentScope.ARTIST) }
        results.albums.forEach   { tiles += it.toDiscoverTile(ContentScope.ALBUM) }
        results.playlists.forEach{ tiles += it.toDiscoverTile(ContentScope.PLAYLIST) }

        tiles.filter { it.spotifyUri !in approvedUris }
    }.getOrDefault(emptyList())

    // ── Content requests ───────────────────────────────────────────────────────

    /**
     * Creates a content request. Attempts to POST immediately; on failure stores
     * locally as PENDING_UPLOAD so the offline queue can retry.
     *
     * @return the local ID of the created request
     */
    suspend fun requestContent(profileId: String, tile: DiscoverTile): String {
        val localId = UUID.randomUUID().toString()
        val now = Instant.now()

        // Insert locally first so it appears in "Meine Wünsche" immediately
        requestDao.insert(
            LocalContentRequest(
                id          = localId,
                profileId   = profileId,
                spotifyUri  = tile.spotifyUri,
                title       = tile.title,
                imageUrl    = tile.imageUrl,
                artistName  = tile.artistName,
                contentType = tile.type.name,
                status      = "PENDING_UPLOAD",
                requestedAt = now,
            )
        )

        // Try to upload now; OfflineQueue will retry later if this fails
        runCatching {
            val response = apiClient.createContentRequest(
                profileId,
                CreateContentRequestDto(
                    spotifyUri  = tile.spotifyUri,
                    title       = tile.title,
                    contentType = tile.type.name,
                    imageUrl    = tile.imageUrl,
                    artistName  = tile.artistName,
                )
            )
            requestDao.updateStatusAndServerId(localId, "PENDING", response.id)
        }
        // On failure the row stays PENDING_UPLOAD → drained by OfflineQueue

        return localId
    }

    /** Uploads all PENDING_UPLOAD requests for this profile and marks them PENDING. */
    suspend fun drainOfflineRequests(profileId: String) {
        requestDao.getPendingUpload(profileId).forEach { local ->
            runCatching {
                val response = apiClient.createContentRequest(
                    profileId,
                    CreateContentRequestDto(
                        spotifyUri  = local.spotifyUri,
                        title       = local.title,
                        contentType = local.contentType,
                        imageUrl    = local.imageUrl,
                        artistName  = local.artistName,
                    )
                )
                requestDao.updateStatusAndServerId(local.id, "PENDING", response.id)
            }
        }
    }

    /**
     * Refreshes the local request cache from the backend.
     * Upserts PENDING/REJECTED items; removes APPROVED and EXPIRED.
     */
    suspend fun refreshRequests(profileId: String) {
        runCatching {
            val serverRequests = apiClient.fetchContentRequests(profileId)
            serverRequests.forEach { dto ->
                requestDao.updateByServerId(dto.id, dto.status, dto.parentNote)
            }
            requestDao.deleteApprovedAndExpired(profileId)
        }
    }

    /** Reactive stream of visible requests (PENDING_UPLOAD + PENDING + REJECTED). */
    fun getVisibleRequests(profileId: String): Flow<List<PendingRequest>> =
        requestDao.getVisible(profileId).map { list ->
            list.mapNotNull { local ->
                val status = when (local.status) {
                    "PENDING", "PENDING_UPLOAD" -> RequestStatus.PENDING
                    "REJECTED"                  -> RequestStatus.REJECTED
                    else                        -> null  // filter APPROVED/EXPIRED
                } ?: return@mapNotNull null

                PendingRequest(
                    id          = local.id,
                    tile        = DiscoverTile(
                        spotifyUri = local.spotifyUri,
                        title      = local.title,
                        imageUrl   = local.imageUrl,
                        artistName = local.artistName ?: "",
                        type       = runCatching { ContentType.valueOf(local.contentType) }
                                         .getOrDefault(ContentType.MUSIC),
                        scope      = scopeFromUri(local.spotifyUri),
                    ),
                    status      = status,
                    requestedAt = local.requestedAt,
                    parentNote  = local.parentNote,
                )
            }
        }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private fun DiscoverItemDto.toDiscoverTile(
        explicitScope: ContentScope? = null
    ): DiscoverTile = DiscoverTile(
        spotifyUri = spotifyUri,
        title      = title,
        imageUrl   = imageUrl,
        artistName = artistName ?: "",
        type       = ContentType.MUSIC,   // backend returns no content type; default to MUSIC
        scope      = explicitScope ?: scopeFromUri(spotifyUri),
    )

    private fun scopeFromUri(uri: String): ContentScope = when {
        uri.startsWith("spotify:artist:")   -> ContentScope.ARTIST
        uri.startsWith("spotify:album:")    -> ContentScope.ALBUM
        uri.startsWith("spotify:playlist:") -> ContentScope.PLAYLIST
        uri.startsWith("spotify:track:")    -> ContentScope.TRACK
        else                                -> ContentScope.ARTIST
    }
}
