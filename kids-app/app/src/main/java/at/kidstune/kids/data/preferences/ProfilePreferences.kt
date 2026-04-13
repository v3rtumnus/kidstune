package at.kidstune.kids.data.preferences

import android.content.Context
import androidx.core.content.edit

class ProfilePreferences(context: Context) {

    private val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    var boundProfileId: String?
        get() = prefs.getString(KEY_PROFILE_ID, null)
        set(value) = prefs.edit { putString(KEY_PROFILE_ID, value) }

    var boundProfileName: String?
        get() = prefs.getString(KEY_PROFILE_NAME, null)
        set(value) = prefs.edit { putString(KEY_PROFILE_NAME, value) }

    var boundProfileEmoji: String?
        get() = prefs.getString(KEY_PROFILE_EMOJI, null)
        set(value) = prefs.edit { putString(KEY_PROFILE_EMOJI, value) }

    val isBound: Boolean get() = boundProfileId != null

    /** Whether the family has configured a quick-approval PIN (updated on every sync). */
    var pinAvailable: Boolean
        get() = prefs.getBoolean(KEY_PIN_AVAILABLE, false)
        set(value) = prefs.edit { putBoolean(KEY_PIN_AVAILABLE, value) }

    fun clearBinding() = prefs.edit {
        remove(KEY_PROFILE_ID)
        remove(KEY_PROFILE_NAME)
        remove(KEY_PROFILE_EMOJI)
        remove(KEY_PIN_AVAILABLE)
    }

    companion object {
        private const val PREF_NAME          = "kidstune_profile"
        private const val KEY_PROFILE_ID     = "bound_profile_id"
        private const val KEY_PROFILE_NAME   = "bound_profile_name"
        private const val KEY_PROFILE_EMOJI  = "bound_profile_emoji"
        private const val KEY_PIN_AVAILABLE  = "pin_available"
    }
}
