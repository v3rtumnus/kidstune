package at.kidstune.kids.data.preferences

import android.content.Context
import androidx.core.content.edit

/**
 * Persists the ISO-8601 timestamp of the last successful sync so [SyncWorker]
 * can request a delta instead of a full sync on subsequent runs.
 */
class SyncPreferences(context: Context) {

    private val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    /** ISO-8601 string of the last successful sync, or null if never synced. */
    var lastSyncTimestamp: String?
        get() = prefs.getString(KEY_LAST_SYNC, null)
        set(value) = prefs.edit { putString(KEY_LAST_SYNC, value) }

    fun clear() = prefs.edit { remove(KEY_LAST_SYNC) }

    companion object {
        private const val PREF_NAME      = "kidstune_sync"
        private const val KEY_LAST_SYNC  = "last_sync_timestamp"
    }
}
