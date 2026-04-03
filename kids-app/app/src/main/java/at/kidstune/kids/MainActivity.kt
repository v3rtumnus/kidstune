package at.kidstune.kids

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import at.kidstune.kids.navigation.KidstuneNavHost
import at.kidstune.kids.ui.theme.KidstuneTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KidstuneTheme {
                KidstuneNavHost()
            }
        }
    }
}
