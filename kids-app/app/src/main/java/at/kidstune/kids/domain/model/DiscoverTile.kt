package at.kidstune.kids.domain.model

data class DiscoverTile(
    val spotifyUri: String,
    val title: String,
    val artistName: String,
    val imageUrl: String?,
    val type: ContentType
)
