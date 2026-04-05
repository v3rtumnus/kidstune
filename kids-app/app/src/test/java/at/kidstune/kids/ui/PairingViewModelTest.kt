package at.kidstune.kids.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import at.kidstune.kids.data.preferences.DeviceTokenPreferences
import at.kidstune.kids.data.preferences.PendingProfilesHolder
import at.kidstune.kids.data.remote.KidstuneApiClient
import at.kidstune.kids.data.remote.PairingApiException
import at.kidstune.kids.data.remote.dto.PairingConfirmResponseDto
import at.kidstune.kids.data.remote.dto.PairingProfileDto
import at.kidstune.kids.ui.viewmodel.PairingIntent
import at.kidstune.kids.ui.viewmodel.PairingState
import at.kidstune.kids.ui.viewmodel.PairingViewModel
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PairingViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var apiClient: KidstuneApiClient
    private lateinit var tokenPrefs: DeviceTokenPreferences
    private lateinit var holder: PendingProfilesHolder
    private lateinit var viewModel: PairingViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        apiClient = mockk()
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        tokenPrefs = DeviceTokenPreferences(ctx)
        holder = PendingProfilesHolder()
        viewModel = PairingViewModel(apiClient, tokenPrefs, holder)
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `initial state is EnteringCode with empty digits`() {
        val state = viewModel.state.value
        assertTrue(state is PairingState.EnteringCode)
        assertEquals(emptyList<Int>(), (state as PairingState.EnteringCode).digits)
    }

    @Test
    fun `DigitEntered appends digit to list`() {
        viewModel.onIntent(PairingIntent.DigitEntered(3))

        val state = viewModel.state.value as PairingState.EnteringCode
        assertEquals(listOf(3), state.digits)
    }

    @Test
    fun `DigitEntered ignores 7th digit`() {
        repeat(7) { viewModel.onIntent(PairingIntent.DigitEntered(it % 10)) }

        val state = viewModel.state.value as PairingState.EnteringCode
        assertEquals(6, state.digits.size)
    }

    @Test
    fun `BackspacePressed removes last digit`() {
        viewModel.onIntent(PairingIntent.DigitEntered(1))
        viewModel.onIntent(PairingIntent.DigitEntered(2))
        viewModel.onIntent(PairingIntent.BackspacePressed)

        val state = viewModel.state.value as PairingState.EnteringCode
        assertEquals(listOf(1), state.digits)
    }

    @Test
    fun `BackspacePressed on empty list is a no-op`() {
        viewModel.onIntent(PairingIntent.BackspacePressed)

        val state = viewModel.state.value as PairingState.EnteringCode
        assertEquals(emptyList<Int>(), state.digits)
    }

    @Test
    fun `ConnectPressed with fewer than 6 digits is a no-op`() {
        viewModel.onIntent(PairingIntent.DigitEntered(1))
        viewModel.onIntent(PairingIntent.ConnectPressed)

        assertTrue(viewModel.state.value is PairingState.EnteringCode)
    }

    @Test
    fun `ConnectPressed with 6 digits transitions to Confirming then Success`() = runTest(testDispatcher) {
        val profiles = listOf(PairingProfileDto("id-1", "Luna", "BEAR", "BLUE"))
        coEvery { apiClient.pair(any(), any()) } returns PairingConfirmResponseDto(
            deviceToken = "test-token",
            familyId    = "family-1",
            profiles    = profiles
        )

        repeat(6) { viewModel.onIntent(PairingIntent.DigitEntered(1)) }
        viewModel.onIntent(PairingIntent.ConnectPressed)

        val state = viewModel.state.value
        assertTrue(state is PairingState.Success)
        assertEquals(1, (state as PairingState.Success).profiles.size)
        assertEquals("Luna", state.profiles.first().name)
        assertEquals("🐻", state.profiles.first().emoji)
    }

    @Test
    fun `on success token is stored in DeviceTokenPreferences`() = runTest(testDispatcher) {
        coEvery { apiClient.pair(any(), any()) } returns PairingConfirmResponseDto(
            deviceToken = "stored-token",
            familyId    = "family-1",
            profiles    = emptyList()
        )

        repeat(6) { viewModel.onIntent(PairingIntent.DigitEntered(2)) }
        viewModel.onIntent(PairingIntent.ConnectPressed)

        assertEquals("stored-token", tokenPrefs.token)
    }

    @Test
    fun `on success profiles are stored in PendingProfilesHolder`() = runTest(testDispatcher) {
        val profiles = listOf(
            PairingProfileDto("id-1", "Luna", "BEAR", "BLUE"),
            PairingProfileDto("id-2", "Max",  "FOX",  "GREEN")
        )
        coEvery { apiClient.pair(any(), any()) } returns PairingConfirmResponseDto(
            deviceToken = "token",
            familyId    = "family-1",
            profiles    = profiles
        )

        repeat(6) { viewModel.onIntent(PairingIntent.DigitEntered(0)) }
        viewModel.onIntent(PairingIntent.ConnectPressed)

        assertEquals(2, holder.profiles?.size)
        assertEquals("Luna", holder.profiles?.first()?.name)
    }

    @Test
    fun `PAIRING_CODE_EXPIRED error shows expiry message`() = runTest(testDispatcher) {
        coEvery { apiClient.pair(any(), any()) } throws
            PairingApiException(410, "PAIRING_CODE_EXPIRED", "expired")

        repeat(6) { viewModel.onIntent(PairingIntent.DigitEntered(5)) }
        viewModel.onIntent(PairingIntent.ConnectPressed)

        val state = viewModel.state.value as PairingState.Error
        assertTrue(state.message.contains("abgelaufen", ignoreCase = true))
    }

    @Test
    fun `PAIRING_CODE_NOT_FOUND error shows invalid code message`() = runTest(testDispatcher) {
        coEvery { apiClient.pair(any(), any()) } throws
            PairingApiException(410, "PAIRING_CODE_NOT_FOUND", "not found")

        repeat(6) { viewModel.onIntent(PairingIntent.DigitEntered(9)) }
        viewModel.onIntent(PairingIntent.ConnectPressed)

        val state = viewModel.state.value as PairingState.Error
        assertTrue(state.message.contains("Ungültig", ignoreCase = true))
    }

    @Test
    fun `network error shows connection failed message`() = runTest(testDispatcher) {
        coEvery { apiClient.pair(any(), any()) } throws RuntimeException("timeout")

        repeat(6) { viewModel.onIntent(PairingIntent.DigitEntered(4)) }
        viewModel.onIntent(PairingIntent.ConnectPressed)

        val state = viewModel.state.value as PairingState.Error
        assertTrue(state.message.contains("fehlgeschlagen", ignoreCase = true))
    }

    @Test
    fun `error state retains the digits that were entered`() = runTest(testDispatcher) {
        coEvery { apiClient.pair(any(), any()) } throws
            PairingApiException(410, "PAIRING_CODE_NOT_FOUND", "not found")

        val entered = listOf(1, 2, 3, 4, 5, 6)
        entered.forEach { viewModel.onIntent(PairingIntent.DigitEntered(it)) }
        viewModel.onIntent(PairingIntent.ConnectPressed)

        val state = viewModel.state.value as PairingState.Error
        assertEquals(entered, state.digits)
    }

    @Test
    fun `can retry after error by entering more digits`() = runTest(testDispatcher) {
        coEvery { apiClient.pair(any(), any()) } throws
            PairingApiException(410, "PAIRING_CODE_NOT_FOUND", "not found")

        repeat(6) { viewModel.onIntent(PairingIntent.DigitEntered(9)) }
        viewModel.onIntent(PairingIntent.ConnectPressed)

        // After error, backspace and change last digit
        viewModel.onIntent(PairingIntent.BackspacePressed)
        val state = viewModel.state.value as PairingState.EnteringCode
        assertEquals(5, state.digits.size)
    }
}
