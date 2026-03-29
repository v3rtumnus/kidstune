package com.kidstune.parent.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.kidstune.parent.ui.auth.LoginScreen
import com.kidstune.parent.ui.auth.LoginViewModel
import com.kidstune.parent.ui.content.ContentListScreen
import com.kidstune.parent.ui.content.SearchContentScreen
import com.kidstune.parent.ui.dashboard.DashboardScreen
import com.kidstune.parent.ui.devices.DeviceManagementScreen
import com.kidstune.parent.ui.import.ImportHistoryScreen
import com.kidstune.parent.ui.profile.ProfileDetailScreen
import com.kidstune.parent.ui.requests.ApprovalQueueScreen
import com.kidstune.parent.ui.settings.SettingsScreen

@Composable
fun AppNavGraph(
    navController: NavHostController,
    startDestination: Any,
    loginViewModel: LoginViewModel,
) {
    NavHost(navController = navController, startDestination = startDestination) {

        composable<Login> {
            val state by loginViewModel.state.collectAsStateWithLifecycle()
            LoginScreen(
                state = state,
                onIntent = loginViewModel::onIntent,
                onNavigateToDashboard = {
                    navController.navigate(Dashboard) {
                        popUpTo<Login> { inclusive = true }
                    }
                },
            )
        }

        composable<Dashboard> {
            DashboardScreen()
        }

        composable<ProfileDetail> { backStack ->
            val route = backStack.toRoute<ProfileDetail>()
            ProfileDetailScreen(profileId = route.profileId)
        }

        composable<SearchContent> { backStack ->
            val route = backStack.toRoute<SearchContent>()
            SearchContentScreen(profileId = route.profileId)
        }

        composable<ContentList> { backStack ->
            val route = backStack.toRoute<ContentList>()
            ContentListScreen(profileId = route.profileId)
        }

        composable<ImportHistory> {
            ImportHistoryScreen()
        }

        composable<DeviceManagement> {
            DeviceManagementScreen()
        }

        composable<ApprovalQueue> {
            ApprovalQueueScreen()
        }

        composable<Settings> {
            SettingsScreen()
        }
    }
}