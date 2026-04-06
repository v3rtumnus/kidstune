package at.kidstune.kids.connectivity

import android.content.Context
import android.net.ConnectivityManager
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * Unit tests for [ConnectivityObserver].
 *
 * Robolectric's shadow [ConnectivityManager] has no active network by default.
 * We verify the safe offline default and that the observer initialises without
 * crashing. The callback-driven path (onAvailable / onLost) is exercised
 * indirectly by [at.kidstune.kids.ui.HomeViewModelOfflineTest] through a
 * MutableStateFlow-based stub.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ConnectivityObserverTest {

    private lateinit var context: Context
    private lateinit var cm: ConnectivityManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    }

    // ── Initial emission ──────────────────────────────────────────────────────

    @Test
    fun `emits false initially when no active network`() = runTest {
        // Robolectric provides no active network by default – device is "offline".
        val shadowCm = Shadows.shadowOf(cm)
        shadowCm.setActiveNetworkInfo(null)

        val observer = ConnectivityObserver(context)
        val isOnline = observer.isOnline.first()

        assertFalse("Should be offline when no active network is available", isOnline)
    }

    // ── Offline indicator: no active network ──────────────────────────────────

    @Test
    fun `offline indicator emits false when activeNetwork is null`() = runTest {
        Shadows.shadowOf(cm).setActiveNetworkInfo(null)

        val observer = ConnectivityObserver(context)

        assertFalse(observer.isOnline.first())
    }

    // ── Observer initialises without crashing ─────────────────────────────────

    @Test
    fun `observer collects without throwing when ConnectivityManager has no network`() = runTest {
        // Verifies that the callbackFlow setup and first emission don't throw.
        val observer = ConnectivityObserver(context)
        val result = runCatching { observer.isOnline.first() }
        assertFalse("Should not throw", result.isFailure)
    }
}
