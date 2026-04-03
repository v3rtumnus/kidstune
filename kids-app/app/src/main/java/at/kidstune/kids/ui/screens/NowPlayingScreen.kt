package at.kidstune.kids.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import at.kidstune.kids.ui.components.FavoriteButton
import at.kidstune.kids.ui.theme.KidstuneTheme
import at.kidstune.kids.ui.theme.kidsTouchTarget
import at.kidstune.kids.ui.viewmodel.NowPlayingIntent
import at.kidstune.kids.ui.viewmodel.NowPlayingState
import at.kidstune.kids.ui.viewmodel.NowPlayingViewModel
import coil3.compose.AsyncImage

// ── Stateful entry-point (used by NavHost) ────────────────────────────────

@Composable
fun NowPlayingScreen(
    modifier: Modifier = Modifier,
    viewModel: NowPlayingViewModel = hiltViewModel(),
    onNavigateUp: () -> Unit = {}
) {
    val state by viewModel.state.collectAsState()
    NowPlayingScreen(
        modifier     = modifier,
        state        = state,
        onIntent     = viewModel::onIntent,
        onNavigateUp = onNavigateUp
    )
}

// ── Stateless composable (used in Previews and tests) ─────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(
    modifier: Modifier = Modifier,
    state: NowPlayingState,
    onIntent: (NowPlayingIntent) -> Unit = {},
    onNavigateUp: () -> Unit = {}
) {
    Scaffold(
        modifier = modifier,
        topBar   = {
            TopAppBar(
                title          = { /* intentionally empty – cover art is the focus */ },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(
                            imageVector        = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Zurück"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier            = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ── Cover art (~55 % of available height) ────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.55f)
                    .padding(horizontal = 24.dp, vertical = 8.dp)
            ) {
                AsyncImage(
                    model              = state.imageUrl,
                    contentDescription = "${state.title} – Cover",
                    contentScale       = ContentScale.Crop,
                    modifier           = Modifier
                        .fillMaxSize()
                        .clip(MaterialTheme.shapes.extraLarge)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )
            }

            // ── Info + controls (~45 % of available height) ───────────────
            Column(
                modifier            = Modifier
                    .fillMaxWidth()
                    .weight(0.45f)
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceEvenly
            ) {
                // Title + artist
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text      = state.title,
                        style     = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center,
                        maxLines  = 2,
                        overflow  = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text  = state.artistName,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Progress bar + timestamps
                Column(modifier = Modifier.fillMaxWidth()) {
                    LinearProgressIndicator(
                        progress = { state.progressMs.toFloat() / state.durationMs.toFloat() },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text  = formatMs(state.progressMs),
                            style = MaterialTheme.typography.labelMedium
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            text  = formatMs(state.durationMs),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }

                // Playback controls + favorite
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    // Skip back
                    IconButton(
                        onClick  = { onIntent(NowPlayingIntent.SkipBack) },
                        modifier = Modifier
                            .kidsTouchTarget()
                            .semantics { contentDescription = "Vorheriger Titel" }
                    ) {
                        Icon(
                            imageVector        = Icons.Filled.SkipPrevious,
                            contentDescription = null,
                            modifier           = Modifier.size(36.dp)
                        )
                    }

                    // Play / Pause – larger button
                    IconButton(
                        onClick  = { onIntent(NowPlayingIntent.TogglePlayPause) },
                        modifier = Modifier
                            .size(80.dp)
                            .semantics {
                                contentDescription = if (state.isPlaying) "Pause" else "Abspielen"
                            }
                    ) {
                        Icon(
                            imageVector        = if (state.isPlaying) Icons.Filled.Pause
                                                 else Icons.Filled.PlayArrow,
                            contentDescription = null,
                            tint               = MaterialTheme.colorScheme.primary,
                            modifier           = Modifier.size(52.dp)
                        )
                    }

                    // Skip forward
                    IconButton(
                        onClick  = { onIntent(NowPlayingIntent.SkipForward) },
                        modifier = Modifier
                            .kidsTouchTarget()
                            .semantics { contentDescription = "Nächster Titel" }
                    ) {
                        Icon(
                            imageVector        = Icons.Filled.SkipNext,
                            contentDescription = null,
                            modifier           = Modifier.size(36.dp)
                        )
                    }

                    // Favorite
                    FavoriteButton(
                        isFavorite = state.isFavorite,
                        iconSize   = 36.dp,
                        onAdd      = { onIntent(NowPlayingIntent.ToggleFavorite) },
                        onRemove   = { onIntent(NowPlayingIntent.ToggleFavorite) }
                    )
                }
            }
        }
    }
}

private fun formatMs(ms: Long): String {
    val totalSec = ms / 1_000
    val min = totalSec / 60
    val sec = totalSec % 60
    return "%d:%02d".format(min, sec)
}

// ── Previews ──────────────────────────────────────────────────────────────

@Preview(name = "NowPlayingScreen – playing", showBackground = true, showSystemUi = true)
@Composable
private fun NowPlayingScreenPlayingPreview() {
    KidstuneTheme {
        NowPlayingScreen(state = NowPlayingState(isPlaying = true, isFavorite = false))
    }
}

@Preview(name = "NowPlayingScreen – favorited", showBackground = true, showSystemUi = true)
@Composable
private fun NowPlayingScreenFavoritedPreview() {
    KidstuneTheme {
        NowPlayingScreen(state = NowPlayingState(isPlaying = true, isFavorite = true))
    }
}
