package at.kidstune.kids

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import at.kidstune.kids.data.preferences.ProfilePreferences
import at.kidstune.kids.navigation.HomeRoute
import at.kidstune.kids.navigation.KidstuneNavHost
import at.kidstune.kids.navigation.ProfileSelectionRoute
import at.kidstune.kids.ui.theme.KidstuneTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // ProfilePreferences only needs a Context – create directly to avoid
        // Hilt field-injection which can trigger Kotlin 2.x metadata issues.
        val prefs = ProfilePreferences(applicationContext)
        val startDestination = if (prefs.isBound) HomeRoute else ProfileSelectionRoute

        setContent {
            KidstuneTheme {
                KidstuneNavHost(startDestination = startDestination)
            }
        }
    }
}
