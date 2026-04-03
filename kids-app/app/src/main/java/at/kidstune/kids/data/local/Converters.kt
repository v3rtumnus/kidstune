package at.kidstune.kids.data.local

import androidx.room.TypeConverter
import at.kidstune.kids.domain.model.ContentScope
import at.kidstune.kids.domain.model.ContentType
import java.time.Instant

class Converters {

    @TypeConverter fun fromInstant(value: Long?): Instant? = value?.let { Instant.ofEpochMilli(it) }
    @TypeConverter fun toInstant(instant: Instant?): Long? = instant?.toEpochMilli()

    @TypeConverter fun fromContentType(value: String?): ContentType? = value?.let { ContentType.valueOf(it) }
    @TypeConverter fun toContentType(type: ContentType?): String? = type?.name

    @TypeConverter fun fromContentScope(value: String?): ContentScope? = value?.let { ContentScope.valueOf(it) }
    @TypeConverter fun toContentScope(scope: ContentScope?): String? = scope?.name
}
