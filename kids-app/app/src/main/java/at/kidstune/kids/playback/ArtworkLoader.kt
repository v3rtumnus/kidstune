package at.kidstune.kids.playback

import android.content.Context
import android.graphics.Bitmap
import coil3.BitmapImage
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Loads track artwork as a software-backed [Bitmap] via Coil, with a single-entry
 * cache keyed on URL.
 *
 * **Caching:** if the same [imageUrl] is requested on consecutive state updates
 * (e.g. Spotify emits many PlayerState events for the same track), Coil is called
 * only on the first request; subsequent calls return the cached bitmap immediately.
 *
 * **Hardware bitmaps disabled:** [androidx.media3.common.MediaMetadata] passes the
 * artwork bitmap to the system notification manager, which reads raw pixel data.
 * Hardware-backed bitmaps cannot be read by the CPU, so [allowHardware] is forced
 * to `false`.
 */
@Singleton
class ArtworkLoader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val imageLoader: ImageLoader,
) {
    private var cachedUrl: String? = null
    private var cachedBitmap: Bitmap? = null

    /**
     * Returns a [Bitmap] for [imageUrl], or `null` if [imageUrl] is null or loading fails.
     * Uses the cached bitmap when [imageUrl] matches the previously loaded URL.
     */
    suspend fun load(imageUrl: String?): Bitmap? {
        if (imageUrl == null) {
            cachedUrl = null
            cachedBitmap = null
            return null
        }
        if (imageUrl == cachedUrl) return cachedBitmap

        val request = ImageRequest.Builder(context)
            .data(imageUrl)
            .build()

        val result = imageLoader.execute(request)
        // Hardware bitmaps cannot be read by the CPU (needed for MediaMetadata / notifications).
        // Copy to software-backed ARGB_8888 if Coil returned a hardware bitmap.
        val bitmap = (result as? SuccessResult)
            ?.image
            ?.let { it as? BitmapImage }
            ?.bitmap
            ?.let { bmp ->
                if (bmp.config == Bitmap.Config.HARDWARE) bmp.copy(Bitmap.Config.ARGB_8888, false)
                else bmp
            }

        // Only cache on success. A failed load (network error, 404, etc.) must not
        // poison the cache: the same URL should be retried on the next state update.
        if (bitmap != null) {
            cachedUrl = imageUrl
            cachedBitmap = bitmap
        }
        return bitmap
    }
}
