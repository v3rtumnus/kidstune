package at.kidstune.kids.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import at.kidstune.kids.data.local.entities.LocalAlbum
import at.kidstune.kids.data.mock.MockContentProvider
import at.kidstune.kids.ui.components.ContentTile
import at.kidstune.kids.ui.components.PageIndicator
import at.kidstune.kids.ui.theme.KidstuneTheme
import at.kidstune.kids.ui.viewmodel.AlbumGridIntent
import at.kidstune.kids.ui.viewmodel.AlbumGridNavigation
import at.kidstune.kids.ui.viewmodel.AlbumGridState
import at.kidstune.kids.ui.viewmodel.AlbumGridViewModel

// ── Stateful entry-point (used by NavHost) ────────────────────────────────

@Composable
fun AlbumGridScreen(
    modifier: Modifier = Modifier,
    viewModel: AlbumGridViewModel = hiltViewModel(),
    onNavigateUp: () -> Unit = {},
    onNavigateToTrackList: (albumId: String) -> Unit = {}
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state.navigation) {
        when (val nav = state.navigation) {
            is AlbumGridNavigation.ToTrackList -> {
                onNavigateToTrackList(nav.albumId)
                viewModel.onIntent(AlbumGridIntent.NavigationHandled)
            }
            null -> Unit
        }
    }

    AlbumGridScreen(
        modifier     = modifier,
        state        = state,
        onIntent     = viewModel::onIntent,
        onNavigateUp = onNavigateUp
    )
}

// ── Stateless composable (used in Previews and tests) ─────────────────────

@Composable
fun AlbumGridScreen(
    modifier: Modifier = Modifier,
    state: AlbumGridState,
    onIntent: (AlbumGridIntent) -> Unit = {},
    onNavigateUp: () -> Unit = {}
) {
    val screenTitle = state.contentEntry?.title ?: "Alben"

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
                    text  = screenTitle,
                    style = MaterialTheme.typography.titleLarge
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier         = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center
        ) {
            when {
                state.isLoading -> CircularProgressIndicator()

                state.pages.isEmpty() -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(text = "🎵", fontSize = 64.sp)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text      = "Noch keine Alben verfügbar",
                        style     = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text      = "Warte kurz – die Inhalte werden geladen.",
                        style     = MaterialTheme.typography.bodyMedium,
                        color     = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier  = Modifier.padding(horizontal = 32.dp)
                    )
                }

                else -> {
                    val pagerState = rememberPagerState { state.totalPages }
                    Column(modifier = Modifier.fillMaxSize()) {
                        HorizontalPager(
                            state         = pagerState,
                            flingBehavior = PagerDefaults.flingBehavior(
                                state             = pagerState,
                                snapAnimationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness    = Spring.StiffnessMedium
                                )
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .semantics { testTag = "album_grid_pager" }
                        ) { page ->
                            AlbumPage(
                                albums   = state.pages.getOrElse(page) { emptyList() },
                                onIntent = onIntent
                            )
                        }
                        Box(
                            modifier         = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            PageIndicator(
                                pageCount   = state.totalPages,
                                currentPage = pagerState.currentPage
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AlbumPage(
    modifier: Modifier = Modifier,
    albums: List<LocalAlbum>,
    onIntent: (AlbumGridIntent) -> Unit
) {
    val rows = albums.chunked(2)
    Column(
        modifier            = modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        rows.forEach { pair ->
            Row(
                modifier              = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AlbumTile(
                    album    = pair.getOrNull(0),
                    onIntent = onIntent,
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
                AlbumTile(
                    album    = pair.getOrNull(1),
                    onIntent = onIntent,
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
            }
        }
    }
}

@Composable
private fun AlbumTile(
    modifier: Modifier = Modifier,
    album: LocalAlbum?,
    onIntent: (AlbumGridIntent) -> Unit
) {
    if (album == null) {
        Spacer(modifier = modifier)
        return
    }
    ContentTile(
        modifier  = modifier,
        title     = album.title,
        imageUrl  = album.imageUrl,
        onClick   = { onIntent(AlbumGridIntent.AlbumTapped(album.id)) }
    )
}

// ── Previews ──────────────────────────────────────────────────────────────

@Preview(name = "AlbumGridScreen – Bibi & Tina", showBackground = true, showSystemUi = true)
@Composable
private fun AlbumGridScreenPreview() {
    KidstuneTheme {
        AlbumGridScreen(
            state = AlbumGridState(
                contentEntry = MockContentProvider.contentEntries.first(),
                albums       = MockContentProvider.albums,
                pages        = MockContentProvider.albums.chunked(8)
            )
        )
    }
}
