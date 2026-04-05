package at.kidstune.kids.ui.screen

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import at.kidstune.kids.ui.screens.PairingScreen
import at.kidstune.kids.ui.theme.KidstuneTheme
import at.kidstune.kids.ui.viewmodel.PairingIntent
import at.kidstune.kids.ui.viewmodel.PairingState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PairingScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `should show 10 digit buttons on the number pad`() {
        composeTestRule.setContent {
            KidstuneTheme {
                PairingScreen(state = PairingState.EnteringCode())
            }
        }

        (0..9).forEach { digit ->
            composeTestRule
                .onNodeWithContentDescription("Ziffer $digit")
                .assertIsDisplayed()
        }
    }

    @Test
    fun `Connect button is disabled when fewer than 6 digits entered`() {
        composeTestRule.setContent {
            KidstuneTheme {
                PairingScreen(state = PairingState.EnteringCode(listOf(1, 2, 3)))
            }
        }

        composeTestRule
            .onNodeWithContentDescription("Verbinden")
            .assertIsNotEnabled()
    }

    @Test
    fun `Connect button is enabled when exactly 6 digits entered`() {
        composeTestRule.setContent {
            KidstuneTheme {
                PairingScreen(state = PairingState.EnteringCode(listOf(1, 2, 3, 4, 5, 6)))
            }
        }

        composeTestRule
            .onNodeWithContentDescription("Verbinden")
            .assertIsEnabled()
    }

    @Test
    fun `tapping digit button fires DigitEntered intent`() {
        val intents = mutableListOf<PairingIntent>()

        composeTestRule.setContent {
            KidstuneTheme {
                PairingScreen(
                    state    = PairingState.EnteringCode(),
                    onIntent = { intents += it }
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Ziffer 7").performClick()

        assertEquals(1, intents.size)
        assertEquals(PairingIntent.DigitEntered(7), intents.first())
    }

    @Test
    fun `tapping backspace fires BackspacePressed intent`() {
        val intents = mutableListOf<PairingIntent>()

        composeTestRule.setContent {
            KidstuneTheme {
                PairingScreen(
                    state    = PairingState.EnteringCode(listOf(1)),
                    onIntent = { intents += it }
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Löschen").performClick()

        assertTrue(intents.any { it == PairingIntent.BackspacePressed })
    }

    @Test
    fun `tapping Connect fires ConnectPressed intent`() {
        val intents = mutableListOf<PairingIntent>()

        composeTestRule.setContent {
            KidstuneTheme {
                PairingScreen(
                    state    = PairingState.EnteringCode(listOf(1, 2, 3, 4, 5, 6)),
                    onIntent = { intents += it }
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Verbinden").performClick()

        assertTrue(intents.any { it == PairingIntent.ConnectPressed })
    }

    @Test
    fun `error message is shown in Error state`() {
        val errorMessage = "Ungültiger Code"

        composeTestRule.setContent {
            KidstuneTheme {
                PairingScreen(
                    state = PairingState.Error(
                        message = errorMessage,
                        digits  = listOf(9, 9, 9, 9, 9, 9)
                    )
                )
            }
        }

        composeTestRule.onNodeWithText(errorMessage).assertIsDisplayed()
    }

    @Test
    fun `loading spinner is shown in Confirming state`() {
        composeTestRule.setContent {
            KidstuneTheme {
                PairingScreen(state = PairingState.Confirming)
            }
        }

        composeTestRule
            .onNodeWithContentDescription("Verbindung wird hergestellt")
            .assertIsDisplayed()
    }

    @Test
    fun `number pad is hidden in Confirming state`() {
        composeTestRule.setContent {
            KidstuneTheme {
                PairingScreen(state = PairingState.Confirming)
            }
        }

        composeTestRule
            .onNodeWithContentDescription("Ziffer 5")
            .assertDoesNotExist()
    }

    @Test
    fun `Connect button is disabled in Confirming state`() {
        composeTestRule.setContent {
            KidstuneTheme {
                PairingScreen(state = PairingState.Confirming)
            }
        }

        composeTestRule
            .onNodeWithContentDescription("Verbinden")
            .assertIsNotEnabled()
    }

    @Test
    fun `entering 6 digits via intents enables Connect button`() {
        var state by mutableStateOf<PairingState>(PairingState.EnteringCode())

        composeTestRule.setContent {
            KidstuneTheme {
                PairingScreen(
                    state    = state,
                    onIntent = { intent ->
                        if (intent is PairingIntent.DigitEntered) {
                            val current = (state as? PairingState.EnteringCode)?.digits ?: emptyList()
                            state = PairingState.EnteringCode(current + intent.digit)
                        }
                    }
                )
            }
        }

        composeTestRule
            .onNodeWithContentDescription("Verbinden")
            .assertIsNotEnabled()

        repeat(6) {
            composeTestRule.onNodeWithContentDescription("Ziffer ${it + 1}").performClick()
        }

        composeTestRule
            .onNodeWithContentDescription("Verbinden")
            .assertIsEnabled()
    }

    @Test
    fun `expired code error message is shown`() {
        val errorMessage = "Code abgelaufen – bitte einen neuen Code anfordern"

        composeTestRule.setContent {
            KidstuneTheme {
                PairingScreen(
                    state = PairingState.Error(
                        message = errorMessage,
                        digits  = listOf(1, 2, 3, 4, 5, 6)
                    )
                )
            }
        }

        composeTestRule.onNodeWithText(errorMessage).assertIsDisplayed()
    }
}
