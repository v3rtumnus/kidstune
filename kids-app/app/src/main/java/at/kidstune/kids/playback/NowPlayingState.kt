package at.kidstune.kids.playback

/**
 * Snapshot of what is currently playing.
 *
 * Produced by [PlaybackController] and consumed by [NowPlayingViewModel],
 * [HomeViewModel], and any composable that shows playback info.
 *
 * When [trackUri] is null, nothing is playing and the mini-player bar is hidden.
 */
data class NowPlayingState(
    val trackUri: String?     = null,
    val title: String?        = null,
    val artistName: String?   = null,
    /** URL from Room (LocalTrack/LocalAlbum) – not the Spotify image URI. */
    val imageUrl: String?     = null,
    val durationMs: Long      = 0L,
    val positionMs: Long      = 0L,
    val isPlaying: Boolean    = false,
    val isFavorite: Boolean   = false,
    /**
     * 0-based chapter index within the parent album, non-null for AUDIOBOOK content.
     * Populated by looking up [trackUri] in Room after a player-state update.
     */
    val chapterIndex: Int?    = null,
    /** Total chapters in the parent album; non-null when [chapterIndex] is non-null. */
    val totalChapters: Int?   = null
)
