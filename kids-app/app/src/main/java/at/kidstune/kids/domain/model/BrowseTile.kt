package at.kidstune.kids.domain.model

data class BrowseTile(
    val id: String,
    val title: String,
    val artistName: String,
    val imageUrl: String?,
    val spotifyTrackUri: String
)
