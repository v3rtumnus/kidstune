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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import at.kidstune.kids.data.local.entities.LocalAlbum
import at.kidstune.kids.data.local.entities.LocalTrack
import at.kidstune.kids.data.mock.MockContentProvider
import at.kidstune.kids.domain.model.ContentType
import at.kidstune.kids.ui.theme.KidstuneTheme
import at.kidstune.kids.ui.viewmodel.ChapterListIntent
import at.kidstune.kids.ui.viewmodel.ChapterListState
import at.kidstune.kids.ui.viewmodel.ChapterListViewModel
import coil3.compose.AsyncImage
import java.util.Locale
import java.util.concurrent.TimeUnit

// ── Stateful entry-point (used by NavHost) ────────────────────────────────

@Composable
fun ChapterListScreen(
    modifier: Modifier = Modifier,
    viewModel: ChapterListViewModel = hiltViewModel(),
    onNavigateUp: () -> Unit = {},
    onNavigateToNowPlaying: () -> Unit = {}
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state.navigateToNowPlaying) {
        if (state.navigateToNowPlaying) {
            onNavigateToNowPlaying()
            viewModel.onIntent(ChapterListIntent.NavigationHandled)
        }
    }

    ChapterListScreen(
        modifier     = modifier,
        state        = state,
        onIntent     = viewModel::onIntent,
        onNavigateUp = onNavigateUp
    )
}

// ── Stateless composable (used in Previews and tests) ─────────────────────

@Composable
fun ChapterListScreen(
    modifier: Modifier = Modifier,
    state: ChapterListState,
    onIntent: (ChapterListIntent) -> Unit = {},
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
                    text  = state.album?.title ?: "Kapitel",
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // ── Album header ──────────────────────────────────────────────
            item {
                AlbumHeader(album = state.album, chapterCount = state.chapters.size)
            }

            // ── Chapter rows ─────────────────────────────────────────────
            itemsIndexed(state.chapters) { index, track ->
                ChapterRow(
                    track          = track,
                    trackIndex     = index,
                    isResumeChapter = track.spotifyTrackUri == state.resumeTrackUri,
                    resumePositionMs = state.resumePositionMs,
                    onIntent       = onIntent
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
private fun AlbumHeader(
    modifier: Modifier = Modifier,
    album: LocalAlbum?,
    chapterCount: Int
) {
    Row(
        modifier          = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Album art
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            AsyncImage(
                model              = album?.imageUrl,
                contentDescription = null,
                contentScale       = ContentScale.Crop,
                modifier           = Modifier.fillMaxSize()
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text     = album?.title ?: "Hörbuch",
                style    = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text  = "$chapterCount Kapitel",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ChapterRow(
    modifier: Modifier = Modifier,
    track: LocalTrack,
    trackIndex: Int,
    isResumeChapter: Boolean,
    resumePositionMs: Long,
    onIntent: (ChapterListIntent) -> Unit
) {
    Row(
        modifier          = modifier
            .fillMaxWidth()
            .height(72.dp)
            .clickable { onIntent(ChapterListIntent.ChapterTapped(track, trackIndex)) }
            .padding(horizontal = 16.dp)
            .semantics { contentDescription = track.title },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Chapter number / resume indicator
        Box(
            modifier         = Modifier.size(36.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isResumeChapter) {
                Icon(
                    imageVector        = Icons.Default.PlayArrow,
                    contentDescription = "Weiter hören",
                    tint               = MaterialTheme.colorScheme.primary,
                    modifier           = Modifier.size(28.dp)
                )
            } else {
                Icon(
                    imageVector        = Icons.Default.Headphones,
                    contentDescription = null,
                    tint               = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier           = Modifier.size(20.dp)
                )
            }
        }

        // Title + progress bar (resume chapter only)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text     = track.title,
                style    = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color    = if (isResumeChapter) MaterialTheme.colorScheme.primary
                           else MaterialTheme.colorScheme.onSurface
            )
            if (isResumeChapter && resumePositionMs > 0L && track.durationMs > 0L) {
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { (resumePositionMs.toFloat() / track.durationMs.toFloat()).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp),
                    color    = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }
        }

        Spacer(Modifier.width(4.dp))

        // Duration
        Text(
            text  = formatDurationHh(track.durationMs),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun formatDurationHh(durationMs: Long): String {
    val minutes = TimeUnit.MILLISECONDS.toMinutes(durationMs)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(durationMs) % 60
    return String.format(Locale.US, "%d:%02d", minutes, seconds)
}

// ── Previews ──────────────────────────────────────────────────────────────

@Preview(name = "ChapterListScreen – TKKG 200", showBackground = true, showSystemUi = true)
@Composable
private fun ChapterListScreenPreview() {
    KidstuneTheme {
        ChapterListScreen(
            state = ChapterListState(
                album           = MockContentProvider.albums.first { it.id == "album-tkkg-200" },
                chapters        = MockContentProvider.tracks.filter { it.albumId == "album-tkkg-200" },
                resumeTrackUri  = "spotify:track:tkkg200t2",
                resumePositionMs = 600_000L
            )
        )
    }
}

@Preview(name = "ChapterListScreen – no resume", showBackground = true, showSystemUi = true)
@Composable
private fun ChapterListScreenNoResumePreview() {
    KidstuneTheme {
        val tkkg = MockContentProvider.albums.first { it.contentType == ContentType.AUDIOBOOK }
        ChapterListScreen(
            state = ChapterListState(
                album    = tkkg,
                chapters = MockContentProvider.tracks.filter { it.albumId == tkkg.id }
            )
        )
    }
}
