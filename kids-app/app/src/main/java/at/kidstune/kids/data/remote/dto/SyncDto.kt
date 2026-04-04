package at.kidstune.kids.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Client-side mirrors of the backend's FullSyncPayload record.
 * All fields use lenient defaults so future backend additions don't break parsing.
 */

@Serializable
data class SyncPayloadDto(
    val profile: SyncProfileDto,
    val favorites: List<SyncFavoriteDto> = emptyList(),
    val content: List<SyncContentEntryDto> = emptyList(),
    val syncTimestamp: String = ""
)

@Serializable
data class SyncProfileDto(
    val id: String,
    val name: String,
    val avatarIcon: String? = null,
    val avatarColor: String? = null,
    val ageGroup: String? = null
)

@Serializable
data class SyncContentEntryDto(
    val id: String,
    val spotifyUri: String,
    /** One of: TRACK, ALBUM, PLAYLIST, ARTIST */
    val scope: String,
    /** One of: MUSIC, AUDIOBOOK */
    val contentType: String,
    val title: String,
    val imageUrl: String? = null,
    val artistName: String? = null,
    val albums: List<SyncAlbumDto> = emptyList()
)

@Serializable
data class SyncAlbumDto(
    val spotifyAlbumUri: String,
    val title: String,
    val imageUrl: String? = null,
    val releaseDate: String? = null,
    /** One of: MUSIC, AUDIOBOOK */
    val contentType: String,
    val tracks: List<SyncTrackDto> = emptyList()
)

@Serializable
data class SyncTrackDto(
    val spotifyTrackUri: String,
    val title: String,
    val artistName: String? = null,
    val durationMs: Long? = null,
    val trackNumber: Int? = null,
    val discNumber: Int? = null,
    val imageUrl: String? = null
)

@Serializable
data class SyncFavoriteDto(
    val spotifyTrackUri: String,
    val trackTitle: String,
    val trackImageUrl: String? = null,
    val artistName: String? = null,
    val addedAt: String? = null
)
