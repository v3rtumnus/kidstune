package at.kidstune.kids.playback

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import coil3.BitmapImage
import coil3.ImageLoader
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for [ArtworkLoader].
 *
 * Verifies:
 *  - Same [imageUrl] on consecutive [ArtworkLoader.load] calls → Coil execute called once.
 *  - Different [imageUrl] → Coil execute called for each distinct URL.
 *  - null [imageUrl] → returns null and clears the cache.
 *  - Load failure (ErrorResult) → returns null.
 *
 * Robolectric is required because [coil3.request.ImageRequest.Builder] touches Android
 * framework internals (application context, coroutine dispatcher).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ArtworkLoaderTest {

    private lateinit var context: Context
    private lateinit var mockImageLoader: ImageLoader
    private lateinit var artworkLoader: ArtworkLoader

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        mockImageLoader = mockk()
        artworkLoader = ArtworkLoader(context, mockImageLoader)
    }

    @Test
    fun `load same URL twice - Coil execute called only once`() = runTest {
        val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        coEvery { mockImageLoader.execute(any<ImageRequest>()) } returns successResult(bitmap)

        val first = artworkLoader.load("https://example.com/cover.jpg")
        val second = artworkLoader.load("https://example.com/cover.jpg")

        assertEquals(bitmap, first)
        assertEquals(bitmap, second)
        // Cache hit on second call — execute invoked exactly once
        coVerify(exactly = 1) { mockImageLoader.execute(any<ImageRequest>()) }
    }

    @Test
    fun `load different URLs - Coil execute called for each`() = runTest {
        val bitmap1 = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        val bitmap2 = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        coEvery { mockImageLoader.execute(any<ImageRequest>()) }
            .returnsMany(successResult(bitmap1), successResult(bitmap2))

        val first  = artworkLoader.load("https://example.com/cover1.jpg")
        val second = artworkLoader.load("https://example.com/cover2.jpg")

        assertEquals(bitmap1, first)
        assertEquals(bitmap2, second)
        coVerify(exactly = 2) { mockImageLoader.execute(any<ImageRequest>()) }
    }

    @Test
    fun `load null URL - returns null without calling Coil`() = runTest {
        val result = artworkLoader.load(null)
        assertNull(result)
        coVerify(exactly = 0) { mockImageLoader.execute(any<ImageRequest>()) }
    }

    @Test
    fun `load null after cached URL - clears cache so next real URL is fetched`() = runTest {
        val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        coEvery { mockImageLoader.execute(any<ImageRequest>()) } returns successResult(bitmap)

        artworkLoader.load("https://example.com/cover.jpg") // populates cache
        artworkLoader.load(null)                             // clears cache
        artworkLoader.load("https://example.com/cover.jpg") // cache was cleared → Coil called again

        coVerify(exactly = 2) { mockImageLoader.execute(any<ImageRequest>()) }
    }

    @Test
    fun `load with Coil error - returns null and does not cache`() = runTest {
        val errorResult: ErrorResult = mockk(relaxed = true)
        coEvery { mockImageLoader.execute(any<ImageRequest>()) } returns errorResult

        val result = artworkLoader.load("https://example.com/cover.jpg")

        assertNull(result)
        // A subsequent call for the same URL must retry (null was cached, not the URL)
        coEvery { mockImageLoader.execute(any<ImageRequest>()) } returns
                successResult(Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888))
        artworkLoader.load("https://example.com/cover.jpg")
        coVerify(exactly = 2) { mockImageLoader.execute(any<ImageRequest>()) }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    // image is a plain val, not a suspend function — use every, not coEvery
    private fun successResult(bitmap: Bitmap): SuccessResult = mockk {
        every { image } returns BitmapImage(bitmap)
    }
}
