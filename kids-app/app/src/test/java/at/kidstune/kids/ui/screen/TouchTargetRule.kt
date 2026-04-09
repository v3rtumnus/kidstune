package at.kidstune.kids.ui.screen

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue

/**
 * Reusable assertion helper that scans every clickable node in the current Compose
 * semantics tree and verifies that both its width and height meet the minimum touch
 * target size required by the KidsTune design (72 dp).
 *
 * Usage:
 * ```kotlin
 * private val touchTargets = TouchTargetRule(composeTestRule)
 *
 * @Test
 * fun `all clickable nodes on home screen meet 72dp minimum`() {
 *     composeTestRule.setContent { KidstuneTheme { HomeScreen(state = HomeState()) } }
 *     touchTargets.assertAll()
 * }
 * ```
 *
 * The rule is intentionally NOT a JUnit `@Rule` because it must access the Compose
 * semantics tree *after* content is rendered, which happens inside each test body.
 * Instantiate it once per test class and call [assertAll] wherever needed.
 */
class TouchTargetRule(
    private val composeTestRule: ComposeContentTestRule,
    private val minTouchTarget: Dp = 72.dp
) {

    /**
     * Fetches all nodes that have a click action and asserts each one is at least
     * [minTouchTarget] × [minTouchTarget] dp in size.
     *
     * Call this after [ComposeContentTestRule.setContent] and any state changes have settled.
     */
    fun assertAll() {
        val density = composeTestRule.density
        val minPx   = with(density) { minTouchTarget.roundToPx() }

        val nodes = composeTestRule
            .onAllNodes(hasClickAction())
            .fetchSemanticsNodes(atLeastOneRootRequired = false)

        nodes.forEach { node ->
            val label: String =
                if (SemanticsProperties.ContentDescription in node.config)
                    node.config[SemanticsProperties.ContentDescription].firstOrNull() ?: "node@${node.id}"
                else if (SemanticsProperties.TestTag in node.config)
                    node.config[SemanticsProperties.TestTag]
                else
                    "node@${node.id}"

            assertTrue(
                "Touch target '$label' width ${node.size.width}px < ${minTouchTarget} ($minPx px)",
                node.size.width >= minPx
            )
            assertTrue(
                "Touch target '$label' height ${node.size.height}px < ${minTouchTarget} ($minPx px)",
                node.size.height >= minPx
            )
        }
    }
}
