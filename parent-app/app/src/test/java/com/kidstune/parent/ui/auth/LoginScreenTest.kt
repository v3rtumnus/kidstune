package com.kidstune.parent.ui.auth

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kidstune.parent.ui.theme.KidstuneParentTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class LoginScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `login screen renders with Spotify login button`() {
        composeTestRule.setContent {
            KidstuneParentTheme {
                LoginScreen(
                    state = LoginState.Idle,
                    onIntent = {},
                    onNavigateToDashboard = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Login with Spotify").assertIsDisplayed()
    }

    @Test
    fun `login screen renders app title`() {
        composeTestRule.setContent {
            KidstuneParentTheme {
                LoginScreen(
                    state = LoginState.Idle,
                    onIntent = {},
                    onNavigateToDashboard = {},
                )
            }
        }

        composeTestRule.onNodeWithText("KidsTune").assertIsDisplayed()
    }

    @Test
    fun `login button is enabled in Idle state`() {
        composeTestRule.setContent {
            KidstuneParentTheme {
                LoginScreen(
                    state = LoginState.Idle,
                    onIntent = {},
                    onNavigateToDashboard = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Login with Spotify").assertIsEnabled()
    }

    @Test
    fun `login button is disabled while Loading`() {
        composeTestRule.setContent {
            KidstuneParentTheme {
                LoginScreen(
                    state = LoginState.Loading,
                    onIntent = {},
                    onNavigateToDashboard = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Login with Spotify").assertIsNotEnabled()
    }

    @Test
    fun `error message is shown in Error state`() {
        composeTestRule.setContent {
            KidstuneParentTheme {
                LoginScreen(
                    state = LoginState.Error("Login failed. Please try again."),
                    onIntent = {},
                    onNavigateToDashboard = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Login failed. Please try again.").assertIsDisplayed()
        composeTestRule.onNodeWithText("Login with Spotify").assertIsDisplayed()
    }

    @Test
    fun `no error message shown in Idle state`() {
        composeTestRule.setContent {
            KidstuneParentTheme {
                LoginScreen(
                    state = LoginState.Idle,
                    onIntent = {},
                    onNavigateToDashboard = {},
                )
            }
        }

        // Login button visible, no error text
        composeTestRule.onNodeWithText("Login with Spotify").assertIsDisplayed()
    }
}