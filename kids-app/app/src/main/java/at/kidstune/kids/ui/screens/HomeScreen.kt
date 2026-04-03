package at.kidstune.kids.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
    onNavigateToNowPlaying: () -> Unit = {}
) {
    val state by viewModel.state.collectAsState()
    HomeScreen(
        modifier              = modifier,
        state                 = state,
        onIntent              = viewModel::onIntent,
        onNavigateToBrowse    = onNavigateToBrowse,
        onNavigateToNowPlaying = onNavigateToNowPlaying
    )
}

// ── Stateless composable (used in Previews and tests) ─────────────────────

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    state: HomeState,
    onIntent: (HomeIntent) -> Unit = {},
    onNavigateToBrowse: (BrowseCategory) -> Unit = {},
    onNavigateToNowPlaying: () -> Unit = {}
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
            // Profile avatar – top-left
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                ProfileBadge(
                    emoji = state.boundProfileEmoji,
                    name  = state.boundProfileName
                )
            }

            // Category buttons – vertically centered in remaining space
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

                // Favorites – centered, slightly narrower
                CategoryButton(
                    emoji    = "❤️",
                    label    = "Lieblingssongs",
                    color    = FavoritePrimary,
                    modifier = Modifier.fillMaxWidth(0.6f),
                    onClick  = { onNavigateToBrowse(BrowseCategory.FAVORITES) }
                )
            }
        }
    }
}

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
    Card(
        modifier  = modifier
            .height(140.dp)
            .kidsTouchTarget()
            .clickable(onClick = onClick)
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

@Preview(name = "HomeScreen – nothing playing", showBackground = true, showSystemUi = true)
@Composable
private fun HomeScreenNothingPlayingPreview() {
    KidstuneTheme {
        HomeScreen(
            state = HomeState(
                nowPlayingTitle  = null,
                nowPlayingArtist = null,
                nowPlayingImageUrl = null
            )
        )
    }
}
