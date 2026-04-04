package at.kidstune.kids.di

import at.kidstune.kids.playback.ApplicationScope
import at.kidstune.kids.playback.SpotifyRemote
import at.kidstune.kids.playback.SpotifyRemoteManager
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
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
    }
}
