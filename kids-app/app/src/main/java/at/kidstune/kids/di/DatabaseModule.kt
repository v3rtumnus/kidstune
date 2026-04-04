package at.kidstune.kids.di

import android.content.Context
import androidx.room.Room
import at.kidstune.kids.data.local.AlbumDao
import at.kidstune.kids.data.local.ContentDao
import at.kidstune.kids.data.local.FavoriteDao
import at.kidstune.kids.data.local.KidstuneDatabase
import at.kidstune.kids.data.local.PlaybackPositionDao
import at.kidstune.kids.data.local.TrackDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides @Singleton
    fun provideDatabase(@ApplicationContext ctx: Context): KidstuneDatabase =
        Room.databaseBuilder(ctx, KidstuneDatabase::class.java, "kidstune.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides fun provideContentDao(db: KidstuneDatabase): ContentDao               = db.contentDao()
    @Provides fun provideAlbumDao(db: KidstuneDatabase): AlbumDao                   = db.albumDao()
    @Provides fun provideTrackDao(db: KidstuneDatabase): TrackDao                   = db.trackDao()
    @Provides fun provideFavoriteDao(db: KidstuneDatabase): FavoriteDao             = db.favoriteDao()
    @Provides fun providePlaybackPositionDao(db: KidstuneDatabase): PlaybackPositionDao = db.playbackPositionDao()
}
