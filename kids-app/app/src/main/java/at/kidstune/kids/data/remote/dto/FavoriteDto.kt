package at.kidstune.kids.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class AddFavoriteRequestDto(
    val spotifyTrackUri: String,
    val trackTitle: String,
    val trackImageUrl: String? = null,
    val artistName: String? = null
)

@Serializable
data class FavoriteResponseDto(
    val id: String,
    val profileId: String,
    val spotifyTrackUri: String,
    val trackTitle: String,
    val trackImageUrl: String? = null,
    val artistName: String? = null,
    val addedAt: String? = null
)
