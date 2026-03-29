package com.kidstune.parent.ui.auth

import app.cash.turbine.test
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import com.kidstune.parent.data.local.AuthPreferences

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val authPreferences = mockk<AuthPreferences>(relaxed = true)
    private val backendBaseUrl = "https://test.kidstune.example.com"
    private lateinit var viewModel: LoginViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = LoginViewModel(authPreferences, backendBaseUrl)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── Initial state ─────────────────────────────────────────────────────────

    @Test
    fun `initial state is Idle`() {
        assertEquals(LoginState.Idle, viewModel.state.value)
    }

    // ── StartLogin ────────────────────────────────────────────────────────────

    @Test
    fun `StartLogin transitions state to Loading`() = runTest {
        viewModel.onIntent(LoginIntent.StartLogin)
        assertEquals(LoginState.Loading, viewModel.state.value)
    }

    @Test
    fun `StartLogin emits OpenBrowser effect with correct URL`() = runTest {
        viewModel.effects.test {
            viewModel.onIntent(LoginIntent.StartLogin)
            val effect = awaitItem()
            assertInstanceOf(LoginEffect.OpenBrowser::class.java, effect)
            assertEquals("$backendBaseUrl/api/v1/auth/spotify/login", (effect as LoginEffect.OpenBrowser).url)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── HandleCallback ────────────────────────────────────────────────────────

    @Test
    fun `HandleCallback stores credentials and transitions to Success`() = runTest {
        viewModel.onIntent(LoginIntent.HandleCallback(familyId = "fam-123", token = "tok-abc"))
        verify { authPreferences.saveAuth("fam-123", "tok-abc") }
        assertEquals(LoginState.Success("fam-123"), viewModel.state.value)
    }

    // ── HandleError ───────────────────────────────────────────────────────────

    @Test
    fun `HandleError transitions state to Error with message`() = runTest {
        viewModel.onIntent(LoginIntent.HandleError("Something went wrong"))
        assertEquals(LoginState.Error("Something went wrong"), viewModel.state.value)
    }

    // ── Full flow ─────────────────────────────────────────────────────────────

    @Test
    fun `full happy path - Idle to Loading to Success`() = runTest {
        viewModel.state.test {
            assertEquals(LoginState.Idle, awaitItem())           // initial

            viewModel.onIntent(LoginIntent.StartLogin)
            assertEquals(LoginState.Loading, awaitItem())        // loading

            viewModel.onIntent(LoginIntent.HandleCallback("fam-1", "tok-1"))
            assertEquals(LoginState.Success("fam-1"), awaitItem()) // success

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `full error path - Idle to Loading to Error`() = runTest {
        viewModel.state.test {
            assertEquals(LoginState.Idle, awaitItem())           // initial

            viewModel.onIntent(LoginIntent.StartLogin)
            assertEquals(LoginState.Loading, awaitItem())        // loading

            viewModel.onIntent(LoginIntent.HandleError("Network timeout"))
            assertEquals(LoginState.Error("Network timeout"), awaitItem()) // error

            cancelAndIgnoreRemainingEvents()
        }
    }
}