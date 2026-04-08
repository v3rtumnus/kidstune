package at.kidstune.kids.data.remote.dto

import kotlinx.serialization.Serializable

/** One item from GET /api/v1/spotify/suggestions or flattened search result. */
@Serializable
data class DiscoverItemDto(
    val id: String,
    val title: String,
    val imageUrl: String? = null,
    val spotifyUri: String,
    val artistName: String? = null,
)

/** Grouped result from GET /api/v1/spotify/search. */
@Serializable
data class SearchResultsDto(
    val artists: List<DiscoverItemDto> = emptyList(),
    val albums: List<DiscoverItemDto> = emptyList(),
    val playlists: List<DiscoverItemDto> = emptyList(),
)

/** Request body for POST /api/v1/profiles/{profileId}/content-requests. */
@Serializable
data class CreateContentRequestDto(
    val spotifyUri: String,
    val title: String,
    val contentType: String,   // "MUSIC" or "AUDIOBOOK"
    val imageUrl: String? = null,
    val artistName: String? = null,
)

/** Response from POST or GET /api/v1/content-requests. */
@Serializable
data class ContentRequestResponseDto(
    val id: String,
    val profileId: String,
    val spotifyUri: String,
    val title: String,
    val imageUrl: String? = null,
    val artistName: String? = null,
    val contentType: String,
    val status: String,          // PENDING | APPROVED | REJECTED | EXPIRED
    val requestedAt: String,
    val parentNote: String? = null,
)
