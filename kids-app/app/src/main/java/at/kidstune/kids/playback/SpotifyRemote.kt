package at.kidstune.kids.playback

import kotlinx.coroutines.flow.StateFlow

/**
 * Abstraction over the Spotify App Remote SDK.
 *
 * [SpotifyRemoteManager] is the real implementation; tests use [FakeSpotifyRemote].
 *
 * All suspend functions throw if the SDK is not connected.
 */
interface SpotifyRemote {

    /** True after a successful [connect] call and until [disconnect] or an error. */
    val isConnected: StateFlow<Boolean>

    /** Non-null when the last connection attempt failed. Cleared on reconnect. */
    val connectionError: StateFlow<SpotifyConnectionError?>

    /**
     * Latest player state pushed by Spotify.
     * Null until Spotify confirms connection and begins emitting state.
     */
    val playerStateFlow: StateFlow<SpotifyPlayerStateInternal?>

    /** Initiate connection to the Spotify app. No-op if already connecting/connected. */
    fun connect()

    /** Tear down the connection. Safe to call when already disconnected. */
    fun disconnect()

    // ── Playback commands ─────────────────────────────────────────────────────

    suspend fun play(contextUri: String)

    suspend fun skipToIndex(contextUri: String, index: Int)

    suspend fun pause()

    suspend fun resume()

    suspend fun skipNext()

    suspend fun skipPrevious()

    suspend fun seekTo(positionMs: Long)
}

/**
 * Internal model that maps a Spotify [com.spotify.protocol.types.PlayerState] to
 * plain data – no SDK types leak beyond [SpotifyRemoteManager].
 */
data class SpotifyPlayerStateInternal(
    val trackUri: String?,
    val trackTitle: String?,
    val artistName: String?,
    /** Raw `spotify:image:xxx` URI — resolved to a bitmap by the Spotify app. */
    val imageUri: String?,
    val durationMs: Long,
    val positionMs: Long,
    val isPaused: Boolean
)
