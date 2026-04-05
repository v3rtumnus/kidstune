package at.kidstune.kids

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import at.kidstune.kids.data.preferences.DeviceTokenPreferences
import at.kidstune.kids.data.preferences.ProfilePreferences
import at.kidstune.kids.navigation.HomeRoute
import at.kidstune.kids.navigation.KidstuneNavHost
import at.kidstune.kids.navigation.PairingRoute
import at.kidstune.kids.navigation.ProfileSelectionRoute
import at.kidstune.kids.playback.PlaybackController
import at.kidstune.kids.playback.SpotifyRemote
import at.kidstune.kids.ui.theme.KidstuneTheme
import at.kidstune.kids.ui.viewmodel.SyncTriggerViewModel
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    // Triggers a full sync once per process lifetime (survives rotation).
    // WorkManager-based background sync is added in prompt 5.4.
    private val syncTriggerViewModel: SyncTriggerViewModel by viewModels()

    // Access playback singletons without @Inject field injection, which avoids Hilt
    // annotation processing issues with Kotlin 2.1 metadata in Hilt 2.52.
    private val spotifyRemote: SpotifyRemote by lazy {
        EntryPointAccessors.fromApplication(applicationContext, PlaybackEntryPoint::class.java)
            .spotifyRemote()
    }
    private val playbackController: PlaybackController by lazy {
        EntryPointAccessors.fromApplication(applicationContext, PlaybackEntryPoint::class.java)
            .playbackController()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Connect to Spotify App Remote SDK on startup.
        // The manager handles reconnection automatically on failure.
        spotifyRemote.connect()

        // Determine start screen based on device setup state:
        //  1. No device token → PairingScreen (first launch, parent enters code)
        //  2. Token present but no profile bound → ProfileSelectionScreen (one-time child binding)
        //  3. Token + profile bound → HomeScreen (normal launch)
        val tokenPrefs = DeviceTokenPreferences(applicationContext)
        val profilePrefs = ProfilePreferences(applicationContext)
        val startDestination = when {
            !tokenPrefs.hasToken  -> PairingRoute
            !profilePrefs.isBound -> ProfileSelectionRoute
            else                  -> HomeRoute
        }

        setContent {
            KidstuneTheme {
                KidstuneNavHost(startDestination = startDestination)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // Flush playback position when the app is backgrounded (child closes app,
        // switches Samsung Kids task, or the screen turns off).
        playbackController.onBackground()
    }

    override fun onDestroy() {
        super.onDestroy()
        spotifyRemote.disconnect()
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface PlaybackEntryPoint {
        fun spotifyRemote(): SpotifyRemote
        fun playbackController(): PlaybackController
    }
}
