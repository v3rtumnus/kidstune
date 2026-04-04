package at.kidstune.kids.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import at.kidstune.kids.data.local.entities.LocalAlbum
import at.kidstune.kids.data.local.entities.LocalContentEntry
import at.kidstune.kids.data.local.entities.LocalFavorite
import at.kidstune.kids.data.local.entities.LocalPlaybackPosition
import at.kidstune.kids.data.local.entities.LocalTrack

@Database(
    entities = [
        LocalContentEntry::class,
        LocalAlbum::class,
        LocalTrack::class,
        LocalFavorite::class,
        LocalPlaybackPosition::class,
    ],
    version = 2,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class KidstuneDatabase : RoomDatabase() {
    abstract fun contentDao(): ContentDao
    abstract fun albumDao(): AlbumDao
    abstract fun trackDao(): TrackDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun playbackPositionDao(): PlaybackPositionDao
}
