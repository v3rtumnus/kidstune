package at.kidstune.kids.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class PinApproveRequestDto(
    val spotifyUri: String,
    val scope: String,
    val title: String,
    val imageUrl: String? = null,
    val artistName: String? = null,
    val pin: String
)
