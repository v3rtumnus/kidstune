package at.kidstune.kids.di

import android.content.Context
import at.kidstune.kids.data.preferences.ProfilePreferences
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PreferencesModule {

    @Provides @Singleton
    fun provideProfilePreferences(@ApplicationContext ctx: Context): ProfilePreferences =
        ProfilePreferences(ctx)
}
