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
)
