package at.kidstune.kids.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import at.kidstune.kids.data.local.entities.LocalAlbum
import at.kidstune.kids.data.local.entities.LocalContentEntry
import at.kidstune.kids.data.local.entities.LocalContentRequest
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
        LocalContentRequest::class,
    ],
    version = 5,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class KidstuneDatabase : RoomDatabase() {
    abstract fun contentDao(): ContentDao
    abstract fun albumDao(): AlbumDao
    abstract fun trackDao(): TrackDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun playbackPositionDao(): PlaybackPositionDao
    abstract fun contentRequestDao(): ContentRequestDao

    companion object {
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE local_content_entry ADD COLUMN last_listened_at INTEGER DEFAULT NULL")
            }
        }
    }
}
