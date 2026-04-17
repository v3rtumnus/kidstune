package at.kidstune.kids.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import at.kidstune.kids.data.local.entities.LocalTrack
import at.kidstune.kids.data.mock.MockContentProvider
import at.kidstune.kids.ui.theme.KidstuneTheme
import at.kidstune.kids.ui.viewmodel.TrackListIntent
import at.kidstune.kids.ui.viewmodel.TrackListState
import at.kidstune.kids.ui.viewmodel.TrackListViewModel
import java.util.Locale
import java.util.concurrent.TimeUnit

// ── Stateful entry-point (used by NavHost) ────────────────────────────────

@Composable
fun TrackListScreen(
    modifier: Modifier = Modifier,
    viewModel: TrackListViewModel = hiltViewModel(),
    onNavigateUp: () -> Unit = {},
    onNavigateToNowPlaying: () -> Unit = {}
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state.navigateToNowPlaying) {
        if (state.navigateToNowPlaying) {
            onNavigateToNowPlaying()
            viewModel.onIntent(TrackListIntent.NavigationHandled)
        }
    }

    TrackListScreen(
        modifier     = modifier,
        state        = state,
        onIntent     = viewModel::onIntent,
        onNavigateUp = onNavigateUp,
    )
}

// ── Stateless composable (used in Previews and tests) ─────────────────────

@Composable
fun TrackListScreen(
    modifier: Modifier = Modifier,
    state: TrackListState,
    onIntent: (TrackListIntent) -> Unit = {},
    onNavigateUp: () -> Unit = {}
) {
    Scaffold(
        modifier = modifier,
        topBar   = {
            Row(
                modifier          = Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick  = onNavigateUp,
                    modifier = Modifier.size(72.dp)
                ) {
                    Icon(
                        imageVector        = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Zurück"
                    )
                }
                Text(
                    text  = state.screenTitle,
                    style = MaterialTheme.typography.titleLarge
                )
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            itemsIndexed(state.tracks) { index, track ->
                TrackRow(
                    track      = track,
                    trackIndex = index,
                    onIntent   = onIntent
                )
                HorizontalDivider(
                    modifier  = Modifier.padding(horizontal = 16.dp),
                    thickness = 0.5.dp,
                    color     = MaterialTheme.colorScheme.outlineVariant
                )
            }
        }
    }
}

@Composable
private fun TrackRow(
    modifier: Modifier = Modifier,
    track: LocalTrack,
    trackIndex: Int,
    onIntent: (TrackListIntent) -> Unit
) {
    Row(
        modifier          = modifier
            .fillMaxWidth()
            .height(72.dp)
            .clickable { onIntent(TrackListIntent.TrackTapped(track, trackIndex)) }
            .padding(horizontal = 16.dp)
            .semantics { contentDescription = track.title },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Track number indicator
        Text(
            text  = "${track.discNumber}-${track.trackNumber}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(28.dp),
        )

        // Music note icon as visual anchor
        Icon(
            imageVector        = Icons.Default.MusicNote,
            contentDescription = null,
            tint               = MaterialTheme.colorScheme.primary,
            modifier           = Modifier.size(24.dp)
        )

        // Title + artist
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text     = track.title,
                style    = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!track.artistName.isNullOrEmpty()) {
                Text(
                    text     = track.artistName,
                    style    = MaterialTheme.typography.bodySmall,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Duration
        Text(
            text  = formatDuration(track.durationMs),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun formatDuration(durationMs: Long): String {
    val minutes = TimeUnit.MILLISECONDS.toMinutes(durationMs)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(durationMs) % 60
    return String.format(Locale.US, "%d:%02d", minutes, seconds)
}

// ── Previews ──────────────────────────────────────────────────────────────

@Preview(name = "TrackListScreen – TKKG Folge 200", showBackground = true, showSystemUi = true)
@Composable
private fun TrackListScreenPreview() {
    KidstuneTheme {
        TrackListScreen(
            state = TrackListState(
                album  = MockContentProvider.albums.first { it.id == "album-tkkg-200" },
                tracks = MockContentProvider.tracks.filter { it.albumId == "album-tkkg-200" }
            )
        )
    }
}
