package at.kidstune.kids.playback

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import at.kidstune.kids.MainActivity
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Foreground service that owns KidsTune's [MediaSession] and media notification.
 *
 * ## Why this service exists
 * Spotify registers its own media notification. On most One UI versions, the
 * notification shade remains accessible when Samsung Kids' time limit expires —
 * a child could tap Spotify's notification and open the Spotify app directly,
 * bypassing the Samsung Kids whitelist. This service closes that gap:
 *
 *   - KidsTune registers an active [MediaSession] so Android promotes it above
 *     Spotify's session in the notification shade.
 *   - The notification's tap [PendingIntent] opens [MainActivity] — not Spotify.
 *   - Play/pause and skip buttons in the notification call back into
 *     [PlaybackController] → Spotify App Remote SDK.
 *
 * ## Lifecycle
 * Started from [MainActivity.onCreate]. Continues running while the process is
 * alive so playback controls remain available after the activity is backgrounded.
 * Stopped automatically by media3 when the player reaches STATE_IDLE and the
 * system decides no session is active.
 *
 * ## Hilt
 * Uses [EntryPoint] instead of `@AndroidEntryPoint` to avoid annotation-processor
 * issues with Kotlin 2.1 metadata in Hilt 2.52 (same pattern as [MainActivity]).
 */
class KidstuneMediaService : MediaSessionService() {

    private val ep: MediaServiceEntryPoint by lazy {
        EntryPointAccessors.fromApplication(applicationContext, MediaServiceEntryPoint::class.java)
    }

    private val spotifyRemote    get() = ep.spotifyRemote()
    private val playbackController get() = ep.playbackController()
    private val artworkLoader    get() = ep.artworkLoader()
    private val mirrorPlayer     get() = ep.mirrorPlayer()

    private var mediaSession: MediaSession? = null

    // Scoped to the service lifetime; cancelled in onDestroy.
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // ── Service lifecycle ─────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()

        // Tapping the notification returns the user to KidsTune, not Spotify.
        val openKidsTune = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        mediaSession = MediaSession.Builder(this, mirrorPlayer)
            .setSessionActivity(openKidsTune)
            .build()

        observePlayback()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onDestroy() {
        serviceScope.cancel()
        mediaSession?.release()
        mediaSession = null
        super.onDestroy()
    }

    // ── Playback observation ──────────────────────────────────────────────────

    private fun observePlayback() {
        // Mirror NowPlayingState → SpotifyMirrorPlayer (incl. artwork from Room image URL).
        // collectLatest cancels in-flight artwork loads when the track changes quickly,
        // ensuring the notification always reflects the current track.
        serviceScope.launch {
            playbackController.nowPlaying.collectLatest { nowPlaying ->
                val bitmap = artworkLoader.load(nowPlaying.imageUrl)
                withContext(Dispatchers.Main) {
                    mirrorPlayer.updateState(nowPlaying, bitmap)
                }
            }
        }

        // Clear the notification when the Spotify App Remote disconnects (e.g. user
        // force-stops Spotify). Keeps collecting so reconnect will re-populate state.
        serviceScope.launch {
            spotifyRemote.isConnected.collect { connected ->
                if (!connected) {
                    withContext(Dispatchers.Main) {
                        mirrorPlayer.clearState()
                    }
                }
            }
        }
    }

    // ── Hilt entry point ──────────────────────────────────────────────────────

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface MediaServiceEntryPoint {
        fun spotifyRemote(): SpotifyRemote
        fun playbackController(): PlaybackController
        fun artworkLoader(): ArtworkLoader
        fun mirrorPlayer(): SpotifyMirrorPlayer
    }
}
