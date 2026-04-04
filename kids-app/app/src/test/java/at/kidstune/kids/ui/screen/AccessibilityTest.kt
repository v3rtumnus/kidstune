package at.kidstune.kids.ui.screen

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.dp
import at.kidstune.kids.data.mock.MockContentProvider
import at.kidstune.kids.domain.model.BrowseCategory
import at.kidstune.kids.domain.model.mockProfiles
import at.kidstune.kids.ui.screens.BrowseScreen
import at.kidstune.kids.ui.screens.HomeScreen
import at.kidstune.kids.ui.screens.NowPlayingScreen
import at.kidstune.kids.ui.screens.ProfileSelectionScreen
import at.kidstune.kids.ui.theme.KidstuneTheme
import at.kidstune.kids.ui.viewmodel.BrowseState
import at.kidstune.kids.ui.viewmodel.HomeState
import at.kidstune.kids.ui.viewmodel.NowPlayingState
import at.kidstune.kids.ui.viewmodel.ProfileSelectionState
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Accessibility checks across all screens.
 *
 * WCAG AA text contrast is enforced by the Material 3 color system (KidstuneTheme).
 * The theme intentionally disables dynamic colors to preserve the category color coding
 * (Music=blue, Audiobooks=green, Favorites=pink) which all pass AA contrast against white.
 * Manual verification: run the Accessibility Scanner on a connected device after installing
 * the debug APK to confirm contrast ratios.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AccessibilityTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // ── Content descriptions ───────────────────────────────────────────────

    @Test
    fun `profile selection tiles have content descriptions`() {
        composeTestRule.setContent {
            KidstuneTheme {
                ProfileSelectionScreen(
                    state = ProfileSelectionState(profiles = mockProfiles)
                )
            }
        }

        mockProfiles.forEach { profile ->
            composeTestRule
                .onNodeWithContentDescription("Profil: ${profile.name}")
                .assertIsDisplayed()
        }
    }

    @Test
    fun `home screen interactive elements have content descriptions`() {
        composeTestRule.setContent {
            KidstuneTheme { HomeScreen(state = HomeState()) }
        }

        listOf("Musik", "Hörbücher", "Lieblingssongs").forEach { label ->
            composeTestRule.onNodeWithContentDescription(label).assertIsDisplayed()
        }
        composeTestRule
            .onNodeWithContentDescription("Jetzt läuft: Bibi & Tina – Folge 1")
            .assertIsDisplayed()
    }

    @Test
    fun `now playing cover art has content description`() {
        val title = "Bibi & Tina – Folge 1"
        composeTestRule.setContent {
            KidstuneTheme { NowPlayingScreen(state = NowPlayingState(title = title)) }
        }

        composeTestRule
            .onNodeWithContentDescription("$title – Cover")
            .assertIsDisplayed()
    }

    @Test
    fun `browse tiles have content descriptions matching their titles`() {
        val entries = MockContentProvider.contentEntries
            .filter { it.contentType.name == "MUSIC" }
        composeTestRule.setContent {
            KidstuneTheme {
                BrowseScreen(
                    state = BrowseState(
                        category = BrowseCategory.MUSIC,
                        entries  = entries,
                        pages    = entries.chunked(4)
                    )
                )
            }
        }

        // First page tiles should all have content descriptions
        entries.take(4).forEach { entry ->
            composeTestRule.onNodeWithContentDescription(entry.title).assertIsDisplayed()
        }
    }

    // ── 72dp minimum touch targets ─────────────────────────────────────────

    @Test
    fun `home screen interactive elements meet 72dp minimum touch target`() {
        composeTestRule.setContent {
            KidstuneTheme { HomeScreen(state = HomeState()) }
        }

        assertTouchTargets(
            descriptions = listOf("Musik", "Hörbücher", "Lieblingssongs")
        )
    }

    @Test
    fun `now playing controls meet 72dp minimum touch target`() {
        composeTestRule.setContent {
            KidstuneTheme { NowPlayingScreen(state = NowPlayingState(isPlaying = true)) }
        }

        assertTouchTargets(
            descriptions = listOf(
                "Vorheriger Titel",
                "Pause",
                "Nächster Titel",
                "Zu Lieblingssongs hinzufügen",
                "Zurück"
            )
        )
    }

    @Test
    fun `browse screen back button meets 72dp minimum touch target`() {
        val entries = MockContentProvider.contentEntries
            .filter { it.contentType.name == "MUSIC" }
        composeTestRule.setContent {
            KidstuneTheme {
                BrowseScreen(
                    state = BrowseState(
                        category = BrowseCategory.MUSIC,
                        entries  = entries,
                        pages    = entries.chunked(4)
                    )
                )
            }
        }

        assertTouchTargets(descriptions = listOf("Zurück"))
    }

    // ── Semantic roles ─────────────────────────────────────────────────────

    @Test
    fun `now playing favorite button has Button role`() {
        composeTestRule.setContent {
            KidstuneTheme { NowPlayingScreen(state = NowPlayingState()) }
        }

        // FavoriteButton declares role = Role.Button so assistive tech treats it as a button
        val favoriteNode = composeTestRule
            .onAllNodes(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.Role,
                    androidx.compose.ui.semantics.Role.Button
                ) and hasContentDescription("Zu Lieblingssongs hinzufügen")
            )
        favoriteNode[0].assertIsDisplayed()
    }

    // ── Helper ─────────────────────────────────────────────────────────────

    private fun assertTouchTargets(descriptions: List<String>) {
        val density = composeTestRule.density
        val minPx = with(density) { 72.dp.roundToPx() }

        descriptions.forEach { desc ->
            val node = composeTestRule
                .onNodeWithContentDescription(desc)
                .fetchSemanticsNode("Element with contentDescription='$desc' not found")

            assertTrue(
                "Touch target '$desc' width ${node.size.width}px is less than 72dp (${minPx}px)",
                node.size.width >= minPx
            )
            assertTrue(
                "Touch target '$desc' height ${node.size.height}px is less than 72dp (${minPx}px)",
                node.size.height >= minPx
            )
        }
    }
}
