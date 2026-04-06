package at.kidstune.kids.ui

import at.kidstune.kids.connectivity.ConnectivityObserver
import at.kidstune.kids.data.local.ContentDao
import at.kidstune.kids.data.preferences.ProfilePreferences
import at.kidstune.kids.data.preferences.SyncPreferences
import at.kidstune.kids.playback.NowPlayingState
import at.kidstune.kids.playback.PlaybackController
import at.kidstune.kids.ui.viewmodel.HomeViewModel
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Unit tests for the offline-hardening additions to [HomeViewModel]:
 * - [HomeState.isOffline] reflects [ConnectivityObserver.isOnline]
 * - [HomeState.isStaleContent] is true only when online AND last sync > 24 h ago
 * - [HomeState.cachedContentCount] mirrors the Room content count Flow
 * - [HomeViewModel.isStaleContent] boundary cases
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HomeViewModelOfflineTest {

    private val testDispatcher = StandardTestDispatcher()

    private val connectivityObserver = mockk<ConnectivityObserver>()
    private val syncPrefs            = mockk<SyncPreferences>()
    private val contentDao           = mockk<ContentDao>()
    private val playbackController   = mockk<PlaybackController>()
    private val profilePrefs         = mockk<ProfilePreferences>()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        every { profilePrefs.boundProfileId   } returns "profile-1"
        every { profilePrefs.boundProfileName } returns "Luna"
        every { profilePrefs.boundProfileEmoji} returns "🐻"
        every { playbackController.nowPlaying } returns MutableStateFlow(NowPlayingState())
        every { contentDao.countAllFlow("profile-1") } returns flowOf(5)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildViewModel(
        onlineFlow: MutableStateFlow<Boolean> = MutableStateFlow(true)
    ): HomeViewModel {
        every { connectivityObserver.isOnline } returns onlineFlow
        return HomeViewModel(profilePrefs, playbackController, contentDao, syncPrefs, connectivityObserver)
    }

    // ── isOffline mirrors connectivity ────────────────────────────────────────

    @Test
    fun `isOffline is true when connectivity flow emits false`() = runTest {
        val onlineFlow = MutableStateFlow(false)
        every { syncPrefs.lastSyncTimestamp } returns null
        val vm = buildViewModel(onlineFlow)

        advanceUntilIdle()

        assertTrue(vm.state.value.isOffline)
    }

    @Test
    fun `isOffline is false when connectivity flow emits true`() = runTest {
        val onlineFlow = MutableStateFlow(true)
        every { syncPrefs.lastSyncTimestamp } returns null
        val vm = buildViewModel(onlineFlow)

        advanceUntilIdle()

        assertFalse(vm.state.value.isOffline)
    }

    @Test
    fun `isOffline transitions from true to false when network becomes available`() = runTest {
        val onlineFlow = MutableStateFlow(false)
        every { syncPrefs.lastSyncTimestamp } returns null
        val vm = buildViewModel(onlineFlow)
        advanceUntilIdle()
        assertTrue(vm.state.value.isOffline)

        onlineFlow.emit(true)
        advanceUntilIdle()

        assertFalse(vm.state.value.isOffline)
    }

    @Test
    fun `isOffline transitions from false to true when network is lost`() = runTest {
        val onlineFlow = MutableStateFlow(true)
        every { syncPrefs.lastSyncTimestamp } returns null
        val vm = buildViewModel(onlineFlow)
        advanceUntilIdle()
        assertFalse(vm.state.value.isOffline)

        onlineFlow.emit(false)
        advanceUntilIdle()

        assertTrue(vm.state.value.isOffline)
    }

    // ── isStaleContent detection ──────────────────────────────────────────────

    @Test
    fun `isStaleContent is false when offline regardless of last sync age`() = runTest {
        val onlineFlow = MutableStateFlow(false)
        every { syncPrefs.lastSyncTimestamp } returns Instant.now().minus(48, ChronoUnit.HOURS).toString()
        val vm = buildViewModel(onlineFlow)

        advanceUntilIdle()

        assertFalse(vm.state.value.isStaleContent)
    }

    @Test
    fun `isStaleContent is false when online and last sync was recent`() = runTest {
        val onlineFlow = MutableStateFlow(true)
        every { syncPrefs.lastSyncTimestamp } returns Instant.now().minus(1, ChronoUnit.HOURS).toString()
        val vm = buildViewModel(onlineFlow)

        advanceUntilIdle()

        assertFalse(vm.state.value.isStaleContent)
    }

    @Test
    fun `isStaleContent is true when online and last sync was over 24 hours ago`() = runTest {
        val onlineFlow = MutableStateFlow(true)
        every { syncPrefs.lastSyncTimestamp } returns Instant.now().minus(25, ChronoUnit.HOURS).toString()
        val vm = buildViewModel(onlineFlow)

        advanceUntilIdle()

        assertTrue(vm.state.value.isStaleContent)
    }

    @Test
    fun `isStaleContent is false when online and no lastSyncTimestamp stored`() = runTest {
        val onlineFlow = MutableStateFlow(true)
        every { syncPrefs.lastSyncTimestamp } returns null
        val vm = buildViewModel(onlineFlow)

        advanceUntilIdle()

        assertFalse(vm.state.value.isStaleContent)
    }

    @Test
    fun `isStaleContent becomes false when device goes offline even if content is stale`() = runTest {
        val onlineFlow = MutableStateFlow(true)
        every { syncPrefs.lastSyncTimestamp } returns Instant.now().minus(25, ChronoUnit.HOURS).toString()
        val vm = buildViewModel(onlineFlow)
        advanceUntilIdle()
        assertTrue(vm.state.value.isStaleContent)

        onlineFlow.emit(false)
        advanceUntilIdle()

        assertFalse(vm.state.value.isStaleContent)
    }

    // ── isStaleContent unit-level boundary checks ─────────────────────────────

    @Test
    fun `isStaleContent helper returns false when isOnline is false`() {
        every { syncPrefs.lastSyncTimestamp } returns Instant.now().minus(25, ChronoUnit.HOURS).toString()
        every { connectivityObserver.isOnline } returns flowOf(false)
        val vm = HomeViewModel(profilePrefs, playbackController, contentDao, syncPrefs, connectivityObserver)

        assertFalse(vm.isStaleContent(isOnline = false))
    }

    @Test
    fun `isStaleContent helper returns false for timestamp just under the 24h threshold`() {
        // 23h59m ago – clearly within the 24h window, not yet stale
        every { syncPrefs.lastSyncTimestamp } returns Instant.now().minus(23, ChronoUnit.HOURS).minusSeconds(59 * 60).toString()
        every { connectivityObserver.isOnline } returns flowOf(true)
        val vm = HomeViewModel(profilePrefs, playbackController, contentDao, syncPrefs, connectivityObserver)

        assertFalse(vm.isStaleContent(isOnline = true))
    }

    @Test
    fun `isStaleContent helper returns true for timestamp beyond 24h boundary`() {
        every { syncPrefs.lastSyncTimestamp } returns Instant.now().minus(24, ChronoUnit.HOURS).minusSeconds(1).toString()
        every { connectivityObserver.isOnline } returns flowOf(true)
        val vm = HomeViewModel(profilePrefs, playbackController, contentDao, syncPrefs, connectivityObserver)

        assertTrue(vm.isStaleContent(isOnline = true))
    }

    @Test
    fun `isStaleContent helper returns false when timestamp is malformed`() {
        every { syncPrefs.lastSyncTimestamp } returns "not-a-timestamp"
        every { connectivityObserver.isOnline } returns flowOf(true)
        val vm = HomeViewModel(profilePrefs, playbackController, contentDao, syncPrefs, connectivityObserver)

        assertFalse(vm.isStaleContent(isOnline = true))
    }

    // ── cachedContentCount mirrors Room Flow ──────────────────────────────────

    @Test
    fun `cachedContentCount starts null before first Room emission`() = runTest {
        every { syncPrefs.lastSyncTimestamp } returns null
        every { contentDao.countAllFlow("profile-1") } returns flowOf() // no emission yet
        every { connectivityObserver.isOnline } returns MutableStateFlow(true)
        val vm = HomeViewModel(profilePrefs, playbackController, contentDao, syncPrefs, connectivityObserver)

        // Before any emission the count is still the initial null.
        assertNull(vm.state.value.cachedContentCount)
    }

    @Test
    fun `cachedContentCount reflects zero when Room has no entries`() = runTest {
        every { syncPrefs.lastSyncTimestamp } returns null
        every { contentDao.countAllFlow("profile-1") } returns flowOf(0)
        val onlineFlow = MutableStateFlow(true)
        every { connectivityObserver.isOnline } returns onlineFlow
        val vm = HomeViewModel(profilePrefs, playbackController, contentDao, syncPrefs, connectivityObserver)

        advanceUntilIdle()

        assertTrue(vm.state.value.cachedContentCount == 0)
    }

    @Test
    fun `cachedContentCount updates reactively as Room content changes`() = runTest {
        every { syncPrefs.lastSyncTimestamp } returns null
        val countFlow = MutableStateFlow(0)
        every { contentDao.countAllFlow("profile-1") } returns countFlow
        val onlineFlow = MutableStateFlow(false)
        every { connectivityObserver.isOnline } returns onlineFlow
        val vm = HomeViewModel(profilePrefs, playbackController, contentDao, syncPrefs, connectivityObserver)

        advanceUntilIdle()
        assertTrue(vm.state.value.cachedContentCount == 0)

        // Simulate sync completing and content being inserted into Room.
        countFlow.emit(12)
        advanceUntilIdle()

        assertTrue(vm.state.value.cachedContentCount == 12)
    }
}
