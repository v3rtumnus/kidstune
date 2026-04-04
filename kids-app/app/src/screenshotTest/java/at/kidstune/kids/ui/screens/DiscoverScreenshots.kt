package at.kidstune.kids.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import at.kidstune.kids.data.mock.MockDiscoverData
import at.kidstune.kids.domain.model.PendingRequest
import at.kidstune.kids.domain.model.RequestStatus
import at.kidstune.kids.ui.theme.KidstuneTheme
import at.kidstune.kids.ui.viewmodel.DiscoverState
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Screenshot tests for DiscoverScreen.
 * Run:      ./gradlew updateDebugScreenshotTest   → write reference PNGs
 * Validate: ./gradlew validateDebugScreenshotTest → fail if visuals changed
 */

@Preview(name = "Screen_Discover_Idle", showSystemUi = true)
@Composable
fun DiscoverIdleScreenshot() {
    KidstuneTheme {
        DiscoverScreen(state = DiscoverState())
    }
}

@Preview(name = "Screen_Discover_Search", showSystemUi = true)
@Composable
fun DiscoverSearchScreenshot() {
    KidstuneTheme {
        DiscoverScreen(
            state = DiscoverState(
                query         = "Frozen",
                searchResults = MockDiscoverData.mockSearchResults
            )
        )
    }
}

@Preview(name = "Screen_Discover_WithPending", showSystemUi = true)
@Composable
fun DiscoverWithPendingScreenshot() {
    KidstuneTheme {
        DiscoverScreen(
            state = DiscoverState(
                pendingRequests = MockDiscoverData.mockPendingRequests,
                requestedUris   = MockDiscoverData.mockPendingRequests
                    .map { it.tile.spotifyUri }
                    .toSet()
            )
        )
    }
}

@Preview(name = "Screen_Discover_LimitReached", showSystemUi = true)
@Composable
fun DiscoverLimitReachedScreenshot() {
    val threePending = MockDiscoverData.mockSuggestions.take(3).mapIndexed { i, tile ->
        PendingRequest(
            id          = "req-limit-$i",
            tile        = tile,
            status      = RequestStatus.PENDING,
            requestedAt = Instant.now().minus(i.toLong() + 1, ChronoUnit.HOURS)
        )
    }
    KidstuneTheme {
        DiscoverScreen(
            state = DiscoverState(
                pendingRequests = threePending,
                requestedUris   = threePending.map { it.tile.spotifyUri }.toSet()
            )
        )
    }
}
