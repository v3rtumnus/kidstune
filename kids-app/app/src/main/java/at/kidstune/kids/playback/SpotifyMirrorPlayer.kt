package at.kidstune.kids.playback

import android.graphics.Bitmap
import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.SimpleBasePlayer.PositionSupplier
import androidx.media3.common.util.UnstableApi
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton

private val PLAYBACK_COMMANDS: Player.Commands = Player.Commands.Builder()
    .addAll(
        Player.COMMAND_PLAY_PAUSE,
        Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
        Player.COMMAND_SEEK_TO_NEXT,
        Player.COMMAND_SEEK_TO_PREVIOUS,
    )
    .build()

/**
 * A [SimpleBasePlayer] that mirrors Spotify's playback state into the system
 * [androidx.media3.session.MediaSession] owned by [KidstuneMediaService].
 *
 * KidsTune does **not** drive audio playback — Spotify App Remote does. This player
 * only bridges state (metadata + position) and forwards control commands back to
 * [PlaybackController] → Spotify App Remote SDK.
 *
 * ### Threading
 * [SimpleBasePlayer] enforces that all public [Player] methods are called on the
 * application looper (here: the main looper). [updateState] and [clearState] must
 * therefore be called on the main thread; [KidstuneMediaService] uses
 * `withContext(Dispatchers.Main)` before calling them.
 *
 * ### Position interpolation
 * On each Spotify state update we record (`positionSnapshot`, `positionSnapshotTime`).
 * [getState] is called by the media3 framework whenever it needs the current position
 * (seek bar rendering, transport controls). Each call interpolates elapsed wall-clock
 * time on top of the snapshot, giving a smooth seek bar between Spotify updates.
 */
@OptIn(UnstableApi::class)
@Singleton
class SpotifyMirrorPlayer @Inject constructor(
    private val playbackController: PlaybackController,
    @ApplicationScope private val scope: CoroutineScope,
) : SimpleBasePlayer(Looper.getMainLooper()) {

    // Guarded by the main looper — only written from updateState / clearState.
    private var cachedNowPlaying: NowPlayingState? = null
    private var cachedArtworkData: ByteArray? = null
    private var cachedArtworkTrackUri: String? = null
    private var positionSnapshotMs = 0L
    private var positionSnapshotTimeMs = 0L

    // ── SimpleBasePlayer contract ─────────────────────────────────────────────

    override fun getState(): State {
        val nowPlaying = cachedNowPlaying
        if (nowPlaying?.trackUri == null) return idleState()

        val artworkData = cachedArtworkData
        val metadata = MediaMetadata.Builder()
            .setTitle(nowPlaying.title)
            .setArtist(nowPlaying.artistName)
            .apply {
                if (artworkData != null) {
                    setArtworkData(artworkData, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                }
            }
            .build()

        val mediaItem = MediaItem.Builder()
            .setMediaId(nowPlaying.trackUri)
            .setMediaMetadata(metadata)
            .build()

        val durationUs = if (nowPlaying.durationMs > 0) nowPlaying.durationMs * 1_000L else C.TIME_UNSET

        // Capture snapshot values by value so the PositionSupplier lambda remains
        // correct even if updateState is called again before this State is discarded.
        val capturedPositionMs = positionSnapshotMs
        val capturedTimeMs = positionSnapshotTimeMs
        val capturedIsPlaying = nowPlaying.isPlaying
        val positionSupplier = PositionSupplier {
            if (capturedIsPlaying) {
                capturedPositionMs + (System.currentTimeMillis() - capturedTimeMs)
            } else {
                capturedPositionMs
            }
        }

        return State.Builder()
            .setAvailableCommands(PLAYBACK_COMMANDS)
            .setPlaylist(
                ImmutableList.of(
                    MediaItemData.Builder(nowPlaying.trackUri)
                        .setMediaItem(mediaItem)
                        .setDurationUs(durationUs)
                        .build()
                )
            )
            .setCurrentMediaItemIndex(0)
            .setContentPositionMs(positionSupplier)
            .setPlaybackState(Player.STATE_READY)
            .setPlayWhenReady(
                nowPlaying.isPlaying,
                Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST,
            )
            .build()
    }

    /** Forward play/pause notification button taps to Spotify App Remote. */
    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        scope.launch {
            if (playWhenReady) playbackController.resume() else playbackController.pause()
        }
        return Futures.immediateVoidFuture()
    }

    /** Forward seek / skip-next / skip-previous notification button taps to Spotify App Remote. */
    override fun handleSeek(
        mediaItemIndex: Int,
        positionMs: Long,
        seekCommand: Int,
    ): ListenableFuture<*> {
        scope.launch {
            when (seekCommand) {
                Player.COMMAND_SEEK_TO_NEXT,
                Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM  -> playbackController.skipNext()
                Player.COMMAND_SEEK_TO_PREVIOUS,
                Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> playbackController.skipPrevious()
                else -> playbackController.seekTo(positionMs)
            }
        }
        return Futures.immediateVoidFuture()
    }

    override fun handleRelease(): ListenableFuture<*> = Futures.immediateVoidFuture()

    // ── State update API (called from KidstuneMediaService on the main thread) ─

    /**
     * Pushes a new [NowPlayingState] into the player. Triggers a media3 state
     * notification so the system notification updates immediately.
     *
     * Must be called on the main thread.
     */
    fun updateState(nowPlaying: NowPlayingState, artworkBitmap: Bitmap?) {
        cachedNowPlaying = nowPlaying
        // Only re-encode JPEG when the track (and therefore artwork) actually changed.
        if (nowPlaying.trackUri != cachedArtworkTrackUri) {
            cachedArtworkData = artworkBitmap?.toJpegBytes()
            cachedArtworkTrackUri = nowPlaying.trackUri
        }
        positionSnapshotMs = nowPlaying.positionMs
        positionSnapshotTimeMs = System.currentTimeMillis()
        invalidateState()
    }

    /**
     * Clears playback state (e.g. Spotify App Remote disconnected).
     * Causes the system notification to be dismissed by media3.
     *
     * Must be called on the main thread.
     */
    fun clearState() {
        cachedNowPlaying = null
        cachedArtworkData = null
        cachedArtworkTrackUri = null
        positionSnapshotMs = 0L
        invalidateState()
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun idleState(): State = State.Builder()
        .setAvailableCommands(Player.Commands.EMPTY)
        .setPlaybackState(Player.STATE_IDLE)
        .build()
}

/** Encodes to JPEG for embedding in [MediaMetadata]; software bitmaps only. */
private fun Bitmap.toJpegBytes(): ByteArray =
    ByteArrayOutputStream().also { compress(Bitmap.CompressFormat.JPEG, 85, it) }.toByteArray()
