package at.kidstune.kids.ui.screen

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import at.kidstune.kids.domain.model.mockProfiles
import at.kidstune.kids.ui.screens.ProfileSelectionScreen
import at.kidstune.kids.ui.theme.KidstuneTheme
import at.kidstune.kids.ui.viewmodel.ProfileSelectionIntent
import at.kidstune.kids.ui.viewmodel.ProfileSelectionState
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
class ProfileSelectionScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `should show all mock profiles as tiles`() {
        composeTestRule.setContent {
            KidstuneTheme {
                ProfileSelectionScreen(
                    state = ProfileSelectionState(profiles = mockProfiles)
                )
            }
        }

        mockProfiles.forEach { profile ->
            composeTestRule
                .onNodeWithContentDescription("Profil: ${profile.name}")
                .assertIsDisplayed()
        }
    }

    @Test
    fun `tapping profile tile shows confirmation dialog`() {
        var state by mutableStateOf(ProfileSelectionState(profiles = mockProfiles))

        composeTestRule.setContent {
            KidstuneTheme {
                ProfileSelectionScreen(
                    state    = state,
                    onIntent = { intent ->
                        if (intent is ProfileSelectionIntent.SelectProfile)
                            state = state.copy(pendingProfile = intent.profile)
                    }
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Profil: Luna").performClick()

        composeTestRule.onNodeWithText("Bist du Luna?").assertIsDisplayed()
    }

    @Test
    fun `confirming profile calls onProfileBound callback`() {
        var state by mutableStateOf(
            ProfileSelectionState(profiles = mockProfiles, pendingProfile = mockProfiles.first())
        )
        var profileBoundCalled = false

        composeTestRule.setContent {
            KidstuneTheme {
                ProfileSelectionScreen(
                    state    = state,
                    onIntent = { intent ->
                        when (intent) {
                            ProfileSelectionIntent.ConfirmBinding -> {
                                profileBoundCalled = true
                                state = state.copy(pendingProfile = null)
                            }
                            else -> Unit
                        }
                    }
                )
            }
        }

        composeTestRule.onNodeWithText("Ja, ich bin's!").performClick()

        assertTrue("onProfileBound should have been called", profileBoundCalled)
    }

    @Test
    fun `dismissing confirmation dialog hides it`() {
        var state by mutableStateOf(
            ProfileSelectionState(profiles = mockProfiles, pendingProfile = mockProfiles.first())
        )

        composeTestRule.setContent {
            KidstuneTheme {
                ProfileSelectionScreen(
                    state    = state,
                    onIntent = { intent ->
                        if (intent == ProfileSelectionIntent.DismissConfirmation)
                            state = state.copy(pendingProfile = null)
                    }
                )
            }
        }

        composeTestRule.onNodeWithText("Nein").performClick()

        composeTestRule.onNodeWithText("Bist du Luna?").assertDoesNotExist()
    }
}
