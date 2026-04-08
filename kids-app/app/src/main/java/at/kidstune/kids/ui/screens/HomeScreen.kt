package at.kidstune.kids.ui.screens

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import at.kidstune.kids.domain.model.BrowseCategory
import at.kidstune.kids.ui.components.MiniPlayerBar
import at.kidstune.kids.ui.theme.AudiobookPrimary
import at.kidstune.kids.ui.theme.DiscoverPrimary
import at.kidstune.kids.ui.theme.FavoritePrimary
import at.kidstune.kids.ui.theme.KidstuneTheme
import at.kidstune.kids.ui.theme.MusicPrimary
import at.kidstune.kids.ui.theme.kidsTouchTarget
import at.kidstune.kids.ui.viewmodel.HomeIntent
import at.kidstune.kids.ui.viewmodel.HomeState
import at.kidstune.kids.ui.viewmodel.HomeViewModel

// ── Stateful entry-point (used by NavHost) ────────────────────────────────

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
    onNavigateToBrowse: (BrowseCategory) -> Unit = {},
    onNavigateToNowPlaying: () -> Unit = {},
    onNavigateToDiscover: () -> Unit = {}
) {
    val state by viewModel.state.collectAsState()
    HomeScreen(
        modifier               = modifier,
        state                  = state,
        onIntent               = viewModel::onIntent,
        onNavigateToBrowse     = onNavigateToBrowse,
        onNavigateToNowPlaying = onNavigateToNowPlaying,
        onNavigateToDiscover   = onNavigateToDiscover
    )
}

// ── Stateless composable (used in Previews and tests) ─────────────────────

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    state: HomeState,
    onIntent: (HomeIntent) -> Unit = {},
    onNavigateToBrowse: (BrowseCategory) -> Unit = {},
    onNavigateToNowPlaying: () -> Unit = {},
    onNavigateToDiscover: () -> Unit = {}
) {
    Scaffold(
        modifier  = modifier,
        bottomBar = {
            MiniPlayerBar(
                title      = state.nowPlayingTitle,
                artistName = state.nowPlayingArtist,
                imageUrl   = state.nowPlayingImageUrl,
                isPlaying  = state.isPlaying,
                onPlayPause = { onIntent(HomeIntent.TogglePlayPause) },
                onExpand    = onNavigateToNowPlaying
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Top bar: profile avatar (left) + status indicators (right)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                ProfileBadge(
                    emoji    = state.boundProfileEmoji,
                    name     = state.boundProfileName,
                    modifier = Modifier.align(Alignment.CenterStart)
                )

                StatusIndicators(
                    isOffline      = state.isOffline,
                    isStaleContent = state.isStaleContent,
                    modifier       = Modifier.align(Alignment.CenterEnd)
                )
            }

            // No-cache screen: shown when Room is empty (loading = null is treated as
            // having content to avoid a flash on normal cold starts).
            if (state.cachedContentCount == 0) {
                NoCacheScreen(modifier = Modifier.weight(1f))
            } else {
                // Category buttons – vertically centred in remaining space
                Column(
                    modifier            = Modifier
                        .weight(1f)
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Music + Audiobooks side-by-side
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CategoryButton(
                            emoji    = "🎵",
                            label    = "Musik",
                            color    = MusicPrimary,
                            modifier = Modifier.weight(1f),
                            onClick  = { onNavigateToBrowse(BrowseCategory.MUSIC) }
                        )
                        CategoryButton(
                            emoji    = "📖",
                            label    = "Hörbücher",
                            color    = AudiobookPrimary,
                            modifier = Modifier.weight(1f),
                            onClick  = { onNavigateToBrowse(BrowseCategory.AUDIOBOOK) }
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    // Favorites + Discover side-by-side
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CategoryButton(
                            emoji    = "❤️",
                            label    = "Lieblingssongs",
                            color    = FavoritePrimary,
                            modifier = Modifier.weight(1f),
                            onClick  = { onNavigateToBrowse(BrowseCategory.FAVORITES) }
                        )
                        CategoryButton(
                            emoji    = "🔍",
                            label    = "Entdecken",
                            color    = DiscoverPrimary,
                            modifier = Modifier.weight(1f),
                            onClick  = onNavigateToDiscover
                        )
                    }
                }
            }
        }
    }
}

// ── Status indicators (top-right) ─────────────────────────────────────────

/**
 * Shows a cloud-off icon when offline and/or a yellow dot when content is stale.
 * Both are purely informational – they do not block any interaction.
 */
@Composable
private fun StatusIndicators(
    modifier: Modifier = Modifier,
    isOffline: Boolean,
    isStaleContent: Boolean
) {
    if (!isOffline && !isStaleContent) return

    Row(
        modifier              = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment     = Alignment.CenterVertically
    ) {
        if (isStaleContent) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(Color(0xFFFFCC00), CircleShape)
                    .semantics { contentDescription = "Inhalte veraltet" }
            )
        }
        if (isOffline) {
            Icon(
                imageVector        = Icons.Rounded.CloudOff,
                contentDescription = "Offline",
                tint               = MaterialTheme.colorScheme.outline,
                modifier           = Modifier.size(24.dp)
            )
        }
    }
}

// ── Sub-composables ───────────────────────────────────────────────────────

@Composable
private fun ProfileBadge(
    modifier: Modifier = Modifier,
    emoji: String,
    name: String
) {
    Row(
        modifier          = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Surface(
            shape    = CircleShape,
            color    = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(52.dp)
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Text(text = emoji, fontSize = 26.sp)
            }
        }
        Text(
            text  = name,
            style = MaterialTheme.typography.titleMedium
        )
    }
}

@Composable
private fun CategoryButton(
    modifier: Modifier = Modifier,
    emoji: String,
    label: String,
    color: Color,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue   = if (isPressed) 0.95f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label         = "category-button-scale"
    )
    val haptic = LocalHapticFeedback.current

    Card(
        modifier  = modifier
            .height(140.dp)
            .kidsTouchTarget()
            .scale(pressScale)
            .clickable(
                interactionSource = interactionSource,
                indication        = LocalIndication.current,
                onClick           = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick()
                }
            )
            .semantics { contentDescription = label },
        shape     = MaterialTheme.shapes.extraLarge,
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors    = CardDefaults.cardColors(containerColor = color)
    ) {
        Column(
            modifier            = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(text = emoji, fontSize = 40.sp)
            Spacer(Modifier.height(8.dp))
            Text(
                text      = label,
                style     = MaterialTheme.typography.titleMedium,
                color     = Color.White,
                textAlign = TextAlign.Center
            )
        }
    }
}

// ── Previews ──────────────────────────────────────────────────────────────

@Preview(name = "HomeScreen", showBackground = true, showSystemUi = true)
@Composable
private fun HomeScreenPreview() {
    KidstuneTheme {
        HomeScreen(state = HomeState())
    }
}

@Preview(name = "HomeScreen – offline", showBackground = true, showSystemUi = true)
@Composable
private fun HomeScreenOfflinePreview() {
    KidstuneTheme {
        HomeScreen(state = HomeState(isOffline = true))
    }
}

@Preview(name = "HomeScreen – stale content", showBackground = true, showSystemUi = true)
@Composable
private fun HomeScreenStalePreview() {
    KidstuneTheme {
        HomeScreen(state = HomeState(isStaleContent = true))
    }
}

@Preview(name = "HomeScreen – no cache", showBackground = true, showSystemUi = true)
@Composable
private fun HomeScreenNoCachePreview() {
    KidstuneTheme {
        HomeScreen(state = HomeState(isOffline = true, cachedContentCount = 0))
    }
}

@Preview(name = "HomeScreen – nothing playing", showBackground = true, showSystemUi = true)
@Composable
private fun HomeScreenNothingPlayingPreview() {
    KidstuneTheme {
        HomeScreen(
            state = HomeState(
                nowPlayingTitle    = null,
                nowPlayingArtist   = null,
                nowPlayingImageUrl = null
            )
        )
    }
}
