package at.kidstune.kids.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import at.kidstune.kids.data.preferences.ProfilePreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProfilePreferencesTest {

    private lateinit var prefs: ProfilePreferences

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        prefs = ProfilePreferences(ctx)
        prefs.clearBinding()
    }

    @Test
    fun `isBound returns false when no profile stored`() {
        assertFalse(prefs.isBound)
    }

    @Test
    fun `isBound returns true after storing a profile`() {
        prefs.boundProfileId = "profile-luna"
        assertTrue(prefs.isBound)
    }

    @Test
    fun `stores and retrieves profile fields`() {
        prefs.boundProfileId    = "profile-luna"
        prefs.boundProfileName  = "Luna"
        prefs.boundProfileEmoji = "🐻"

        assertEquals("profile-luna", prefs.boundProfileId)
        assertEquals("Luna",         prefs.boundProfileName)
        assertEquals("🐻",           prefs.boundProfileEmoji)
    }

    @Test
    fun `clearBinding removes all stored fields`() {
        prefs.boundProfileId    = "profile-luna"
        prefs.boundProfileName  = "Luna"
        prefs.boundProfileEmoji = "🐻"

        prefs.clearBinding()

        assertNull(prefs.boundProfileId)
        assertNull(prefs.boundProfileName)
        assertNull(prefs.boundProfileEmoji)
        assertFalse(prefs.isBound)
    }
}
