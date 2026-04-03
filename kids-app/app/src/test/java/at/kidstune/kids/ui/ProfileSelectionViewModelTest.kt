package at.kidstune.kids.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import at.kidstune.kids.data.preferences.ProfilePreferences
import at.kidstune.kids.domain.model.mockProfiles
import at.kidstune.kids.ui.viewmodel.ProfileSelectionIntent
import at.kidstune.kids.ui.viewmodel.ProfileSelectionViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProfileSelectionViewModelTest {

    private lateinit var prefs: ProfilePreferences
    private lateinit var viewModel: ProfileSelectionViewModel

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        prefs = ProfilePreferences(ctx)
        prefs.clearBinding()
        viewModel = ProfileSelectionViewModel(prefs)
    }

    @Test
    fun `initial state has all mock profiles and no pending profile`() {
        val state = viewModel.state.value
        assertEquals(mockProfiles, state.profiles)
        assertNull(state.pendingProfile)
    }

    @Test
    fun `SelectProfile sets pendingProfile`() {
        val luna = mockProfiles.first()
        viewModel.onIntent(ProfileSelectionIntent.SelectProfile(luna))

        assertEquals(luna, viewModel.state.value.pendingProfile)
    }

    @Test
    fun `DismissConfirmation clears pendingProfile`() {
        viewModel.onIntent(ProfileSelectionIntent.SelectProfile(mockProfiles.first()))
        viewModel.onIntent(ProfileSelectionIntent.DismissConfirmation)

        assertNull(viewModel.state.value.pendingProfile)
    }

    @Test
    fun `ConfirmBinding writes profile to preferences`() {
        val luna = mockProfiles.first()
        viewModel.onIntent(ProfileSelectionIntent.SelectProfile(luna))
        viewModel.onIntent(ProfileSelectionIntent.ConfirmBinding)

        assertTrue(prefs.isBound)
        assertEquals(luna.id,    prefs.boundProfileId)
        assertEquals(luna.name,  prefs.boundProfileName)
        assertEquals(luna.emoji, prefs.boundProfileEmoji)
    }

    @Test
    fun `ConfirmBinding clears pendingProfile after saving`() {
        viewModel.onIntent(ProfileSelectionIntent.SelectProfile(mockProfiles.first()))
        viewModel.onIntent(ProfileSelectionIntent.ConfirmBinding)

        assertNull(viewModel.state.value.pendingProfile)
    }

    @Test
    fun `ConfirmBinding with no pendingProfile is a no-op`() {
        viewModel.onIntent(ProfileSelectionIntent.ConfirmBinding)

        assertNull(prefs.boundProfileId)
    }
}
