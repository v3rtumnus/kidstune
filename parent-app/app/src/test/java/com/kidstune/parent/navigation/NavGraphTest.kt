package com.kidstune.parent.navigation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kidstune.parent.data.local.AuthPreferences
import com.kidstune.parent.ui.auth.LoginViewModel
import com.kidstune.parent.ui.theme.KidstuneParentTheme
import io.mockk.mockk
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class NavGraphTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun buildLoginViewModel() = LoginViewModel(
        authPreferences = mockk<AuthPreferences>(relaxed = true),
        backendBaseUrl = "https://test.example.com",
    )

    @Test
    fun `nav graph with Login start destination shows login screen`() {
        val viewModel = buildLoginViewModel()
        composeTestRule.setContent {
            KidstuneParentTheme {
                AppNavGraph(
                    navController = rememberNavController(),
                    startDestination = Login,
                    loginViewModel = viewModel,
                )
            }
        }

        composeTestRule.onNodeWithText("Login with Spotify").assertIsDisplayed()
    }

    @Test
    fun `nav graph with Dashboard start destination shows dashboard screen`() {
        val viewModel = buildLoginViewModel()
        composeTestRule.setContent {
            KidstuneParentTheme {
                AppNavGraph(
                    navController = rememberNavController(),
                    startDestination = Dashboard,
                    loginViewModel = viewModel,
                )
            }
        }

        composeTestRule.onNodeWithText("Dashboard").assertIsDisplayed()
    }

    @Test
    fun `stub screens render without crashing - ApprovalQueue`() {
        val viewModel = buildLoginViewModel()
        composeTestRule.setContent {
            KidstuneParentTheme {
                AppNavGraph(
                    navController = rememberNavController(),
                    startDestination = ApprovalQueue,
                    loginViewModel = viewModel,
                )
            }
        }

        composeTestRule.onNodeWithText("Approval Queue").assertIsDisplayed()
    }

    @Test
    fun `stub screens render without crashing - ImportHistory`() {
        val viewModel = buildLoginViewModel()
        composeTestRule.setContent {
            KidstuneParentTheme {
                AppNavGraph(
                    navController = rememberNavController(),
                    startDestination = ImportHistory,
                    loginViewModel = viewModel,
                )
            }
        }

        composeTestRule.onNodeWithText("Import History").assertIsDisplayed()
    }

    @Test
    fun `stub screens render without crashing - DeviceManagement`() {
        val viewModel = buildLoginViewModel()
        composeTestRule.setContent {
            KidstuneParentTheme {
                AppNavGraph(
                    navController = rememberNavController(),
                    startDestination = DeviceManagement,
                    loginViewModel = viewModel,
                )
            }
        }

        composeTestRule.onNodeWithText("Device Management").assertIsDisplayed()
    }

    @Test
    fun `stub screens render without crashing - Settings`() {
        val viewModel = buildLoginViewModel()
        composeTestRule.setContent {
            KidstuneParentTheme {
                AppNavGraph(
                    navController = rememberNavController(),
                    startDestination = Settings,
                    loginViewModel = viewModel,
                )
            }
        }

        composeTestRule.onNodeWithText("Settings").assertIsDisplayed()
    }
}