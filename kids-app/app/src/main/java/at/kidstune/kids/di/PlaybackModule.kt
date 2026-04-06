package at.kidstune.kids.di

import android.content.Context
import at.kidstune.kids.playback.ApplicationScope
import at.kidstune.kids.playback.SpotifyRemote
import at.kidstune.kids.playback.SpotifyRemoteManager
import coil3.ImageLoader
import coil3.SingletonImageLoader
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PlaybackModule {

    /** Bind the concrete manager to the abstraction used by [PlaybackController]. */
    @Binds @Singleton
    abstract fun bindSpotifyRemote(manager: SpotifyRemoteManager): SpotifyRemote

    companion object {
        /**
         * Application-scoped coroutine scope with a SupervisorJob so individual
         * child failures don't cancel the whole scope.
         */
        @Provides @Singleton @ApplicationScope
        fun provideApplicationScope(): CoroutineScope =
            CoroutineScope(SupervisorJob())

        /**
         * Exposes the Coil singleton [ImageLoader] (configured in [at.kidstune.kids.KidstuneApp])
         * for injection into [at.kidstune.kids.playback.ArtworkLoader], enabling
         * mock substitution in unit tests.
         */
        @Provides @Singleton
        fun provideImageLoader(@ApplicationContext context: Context): ImageLoader =
            SingletonImageLoader.get(context)
    }
}
