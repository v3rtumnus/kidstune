package at.kidstune.kids.playback

import android.content.Context
import android.util.Log
import com.spotify.android.appremote.api.ConnectionParams
import com.spotify.android.appremote.api.Connector
import com.spotify.android.appremote.api.SpotifyAppRemote
import com.spotify.protocol.types.PlayerState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "SpotifyRemoteManager"

/**
 * Manages the lifecycle of the Spotify App Remote SDK connection.
 *
 * IMPORTANT: The Spotify App Remote SDK AAR must be placed at
 *   kids-app/libs/spotify-app-remote-release-0.8.0.aar
 * Download from: https://developer.spotify.com/documentation/android/
 * After adding the real AAR, delete com/spotify/android/appremote/api/SpotifyStubs.kt
 * and com/spotify/protocol/ stub files from the source set.
 *
 * Connection lifecycle:
 *  1. [connect] is called when the app starts.
 *  2. On success the subscription to PlayerState begins immediately.
 *  3. On disconnect (SDK callback) a reconnect is scheduled after [RECONNECT_DELAY_MS].
 *  4. [disconnect] should be called when the app permanently exits.
 */
@Singleton
class SpotifyRemoteManager @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope private val scope: CoroutineScope
) : SpotifyRemote {

    companion object {
        private const val CLIENT_ID = "kidstune_spotify_client_id" // replaced at runtime via BuildConfig
        private const val REDIRECT_URI = "kidstune://callback"
        private const val RECONNECT_DELAY_MS = 3_000L
    }

    private val _isConnected = MutableStateFlow(false)
    override val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _connectionError = MutableStateFlow<SpotifyConnectionError?>(null)
    override val connectionError: StateFlow<SpotifyConnectionError?> = _connectionError.asStateFlow()

    private val _playerStateFlow = MutableStateFlow<SpotifyPlayerStateInternal?>(null)
    override val playerStateFlow: StateFlow<SpotifyPlayerStateInternal?> = _playerStateFlow.asStateFlow()

    @Volatile private var remote: SpotifyAppRemote? = null
    @Volatile private var isConnecting = false

    override fun connect() {
        if (_isConnected.value || isConnecting) return
        isConnecting = true
        _connectionError.value = null

        val params = ConnectionParams.Builder(CLIENT_ID)
            .setRedirectUri(REDIRECT_URI)
            .showAuthView(false)
            .build()

        SpotifyAppRemote.connect(context, params, object : Connector.ConnectionListener {
            override fun onConnected(spotifyAppRemote: SpotifyAppRemote) {
                remote = spotifyAppRemote
                isConnecting = false
                _isConnected.value = true
                Log.i(TAG, "Connected to Spotify")
                subscribeToPlayerState(spotifyAppRemote)
            }

            override fun onFailure(throwable: Throwable) {
                isConnecting = false
                _isConnected.value = false
                _connectionError.value = throwable.toConnectionError()
                Log.w(TAG, "Connection failed: ${throwable.message}")
                scheduleReconnect()
            }
        })
    }

    override fun disconnect() {
        remote?.let { SpotifyAppRemote.disconnect(it) }
        remote = null
        _isConnected.value = false
        isConnecting = false
    }

    private fun subscribeToPlayerState(spotifyAppRemote: SpotifyAppRemote) {
        spotifyAppRemote.playerApi
            .subscribeToPlayerState()
            .setEventCallback { playerState: PlayerState ->
                _playerStateFlow.value = playerState.toInternal()
            }
            .setErrorCallback {
                Log.w(TAG, "PlayerState subscription error: ${this.message}")
                _isConnected.value = false
                scheduleReconnect()
            }
    }

    private fun scheduleReconnect() {
        scope.launch {
            delay(RECONNECT_DELAY_MS)
            connect()
        }
    }

    // ── Playback commands ─────────────────────────────────────────────────────

    override suspend fun play(contextUri: String) {
        requireConnected().playerApi.play(contextUri)
    }

    override suspend fun skipToIndex(contextUri: String, index: Int) {
        requireConnected().playerApi.skipToIndex(contextUri, index)
    }

    override suspend fun pause() {
        requireConnected().playerApi.pause()
    }

    override suspend fun resume() {
        requireConnected().playerApi.resume()
    }

    override suspend fun skipNext() {
        requireConnected().playerApi.skipNext()
    }

    override suspend fun skipPrevious() {
        requireConnected().playerApi.skipPrevious()
    }

    override suspend fun seekTo(positionMs: Long) {
        requireConnected().playerApi.seekTo(positionMs)
    }

    private fun requireConnected(): SpotifyAppRemote =
        remote ?: throw IllegalStateException("Not connected to Spotify")

    // ── Mapping helpers ───────────────────────────────────────────────────────

    private fun PlayerState.toInternal() = SpotifyPlayerStateInternal(
        trackUri    = track?.uri,
        trackTitle  = track?.name,
        artistName  = track?.artist?.name,
        imageUri    = track?.imageUri?.raw,
        durationMs  = track?.duration ?: 0L,
        positionMs  = playbackPosition,
        isPaused    = isPaused
    )

    private fun Throwable.toConnectionError(): SpotifyConnectionError {
        val msg = message?.lowercase() ?: ""
        return when {
            "not installed" in msg || "notinstalled" in msg -> SpotifyConnectionError.NOT_INSTALLED
            "not logged" in msg    || "notloggedin"  in msg -> SpotifyConnectionError.NOT_LOGGED_IN
            "premium"     in msg                            -> SpotifyConnectionError.PREMIUM_REQUIRED
            else                                            -> SpotifyConnectionError.OTHER
        }
    }
}
