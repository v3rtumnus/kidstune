package at.kidstune.kids.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import at.kidstune.kids.ui.theme.KidstuneTheme

/**
 * Screenshot tests for all shared UI components.
 * Run: ./gradlew updateDebugScreenshotTest
 * Validate: ./gradlew validateDebugScreenshotTest
 */

// ── ContentTile ───────────────────────────────────────────────────────────

@Preview(name = "ContentTile_Playing", showBackground = true, widthDp = 200)
@Composable
fun ContentTileScreenshot() {
    KidstuneTheme {
        Column(modifier = Modifier.padding(16.dp)) {
            ContentTile(
                title    = "Bibi & Tina – Folge 1",
                imageUrl = null
            )
        }
    }
}

@Preview(name = "ContentTile_WithBadge", showBackground = true, widthDp = 200)
@Composable
fun ContentTileWithBadgeScreenshot() {
    KidstuneTheme {
        Column(modifier = Modifier.padding(16.dp)) {
            ContentTile(
                title     = "TKKG – Folge 200",
                imageUrl  = null,
                badgeText = "NEU"
            )
        }
    }
}

// ── FavoriteButton ────────────────────────────────────────────────────────

@Preview(name = "FavoriteButton_Inactive", showBackground = true, backgroundColor = 0xFF6750A4)
@Composable
fun FavoriteButtonInactiveScreenshot() {
    KidstuneTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            FavoriteButton(isFavorite = false)
        }
    }
}

@Preview(name = "FavoriteButton_Active", showBackground = true, backgroundColor = 0xFF6750A4)
@Composable
fun FavoriteButtonActiveScreenshot() {
    KidstuneTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            FavoriteButton(isFavorite = true)
        }
    }
}

// ── PageIndicator ─────────────────────────────────────────────────────────

@Preview(name = "PageIndicator_Page1of4", showBackground = true)
@Composable
fun PageIndicatorScreenshot() {
    KidstuneTheme {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp)) {
            PageIndicator(pageCount = 4, currentPage = 0)
            PageIndicator(pageCount = 4, currentPage = 2)
            PageIndicator(pageCount = 1, currentPage = 0)
        }
    }
}

// ── MiniPlayerBar ─────────────────────────────────────────────────────────

@Preview(name = "MiniPlayerBar_Playing", showBackground = true)
@Composable
fun MiniPlayerBarPlayingScreenshot() {
    KidstuneTheme {
        Column {
            MiniPlayerBar(
                title      = "Bibi & Tina – Folge 1 – Kapitel 3",
                artistName = "Bibi & Tina",
                imageUrl   = null,
                isPlaying  = true
            )
        }
    }
}

@Preview(name = "MiniPlayerBar_Paused", showBackground = true)
@Composable
fun MiniPlayerBarPausedScreenshot() {
    KidstuneTheme {
        Column {
            MiniPlayerBar(
                title      = "TKKG – Folge 200",
                artistName = "TKKG",
                imageUrl   = null,
                isPlaying  = false
            )
        }
    }
}
