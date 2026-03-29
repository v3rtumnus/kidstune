package com.kidstune.parent

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.compose.rememberNavController
import com.kidstune.parent.data.local.AuthPreferences
import com.kidstune.parent.navigation.AppNavGraph
import com.kidstune.parent.navigation.Dashboard
import com.kidstune.parent.navigation.Login
import com.kidstune.parent.ui.auth.LoginEffect
import com.kidstune.parent.ui.auth.LoginIntent
import com.kidstune.parent.ui.auth.LoginViewModel
import com.kidstune.parent.ui.theme.KidstuneParentTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject
import androidx.lifecycle.Lifecycle

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var authPreferences: AuthPreferences

    private val loginViewModel: LoginViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Handle deep link if app was opened via kidstune://auth/callback
        handleDeepLink(intent)

        // Collect one-shot effects (e.g. open browser for OAuth)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                loginViewModel.effects.collect { effect ->
                    when (effect) {
                        is LoginEffect.OpenBrowser -> openInBrowser(effect.url)
                    }
                }
            }
        }

        setContent {
            KidstuneParentTheme {
                val navController = rememberNavController()
                val startDestination: Any =
                    if (authPreferences.isLoggedIn()) Dashboard else Login

                AppNavGraph(
                    navController = navController,
                    startDestination = startDestination,
                    loginViewModel = loginViewModel,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleDeepLink(intent)
    }

    // ── Deep link handling ────────────────────────────────────────────────────

    /**
     * Processes incoming [Intent] for the OAuth callback deep link
     * `kidstune://auth/callback?familyId=...&token=...`.
     */
    private fun handleDeepLink(intent: Intent) {
        val data: Uri = intent.data ?: return
        if (data.scheme == "kidstune" && data.host == "auth" && data.path?.startsWith("/callback") == true) {
            val familyId = data.getQueryParameter("familyId")
            val token = data.getQueryParameter("token")
            if (!familyId.isNullOrBlank() && !token.isNullOrBlank()) {
                loginViewModel.onIntent(LoginIntent.HandleCallback(familyId, token))
            } else {
                loginViewModel.onIntent(LoginIntent.HandleError("Login failed: invalid callback parameters."))
            }
        }
    }

    private fun openInBrowser(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        startActivity(intent)
    }
}