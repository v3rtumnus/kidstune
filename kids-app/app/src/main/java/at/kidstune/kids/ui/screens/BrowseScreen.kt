package at.kidstune.kids.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import at.kidstune.kids.data.mock.MockContentProvider
import at.kidstune.kids.domain.model.BrowseCategory
import at.kidstune.kids.domain.model.BrowseTile
import at.kidstune.kids.ui.components.ContentTile
import at.kidstune.kids.ui.components.PageIndicator
import at.kidstune.kids.ui.theme.KidstuneTheme
import at.kidstune.kids.ui.viewmodel.BrowseState
import at.kidstune.kids.ui.viewmodel.BrowseViewModel

// ── Stateful entry-point (used by NavHost) ────────────────────────────────

@Composable
fun BrowseScreen(
    modifier: Modifier = Modifier,
    category: BrowseCategory = BrowseCategory.MUSIC,
    viewModel: BrowseViewModel = hiltViewModel(),
    onNavigateUp: () -> Unit = {},
    onNavigateToNowPlaying: () -> Unit = {}
) {
    LaunchedEffect(category) { viewModel.init(category) }
    val state by viewModel.state.collectAsState()
    BrowseScreen(
        modifier               = modifier,
        state                  = state,
        onNavigateUp           = onNavigateUp,
        onNavigateToNowPlaying = onNavigateToNowPlaying
    )
}

// ── Stateless composable (used in Previews and tests) ─────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseScreen(
    modifier: Modifier = Modifier,
    state: BrowseState,
    onNavigateUp: () -> Unit = {},
    onNavigateToNowPlaying: () -> Unit = {}
) {
    val title = when (state.category) {
        BrowseCategory.MUSIC     -> "Musik"
        BrowseCategory.AUDIOBOOK -> "Hörbücher"
        BrowseCategory.FAVORITES -> "Lieblingssongs"
    }

    Scaffold(
        modifier = modifier,
        topBar   = {
            TopAppBar(
                title          = { Text(title) },
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (state.category == BrowseCategory.FAVORITES && state.tiles.isEmpty()) {
                FavoritesEmptyState(modifier = Modifier.fillMaxSize())
            } else if (state.pages.isNotEmpty()) {
                val pagerState = rememberPagerState { state.totalPages }
                Column(modifier = Modifier.fillMaxSize()) {
                    HorizontalPager(
                        state    = pagerState,
                        modifier = Modifier.weight(1f)
                    ) { page ->
                        BrowsePage(
                            tiles   = state.pages.getOrElse(page) { emptyList() },
                            onClick = onNavigateToNowPlaying
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

@Composable
private fun BrowsePage(
    modifier: Modifier = Modifier,
    tiles: List<BrowseTile>,
    onClick: () -> Unit
) {
    Column(
        modifier            = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Row 1
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ContentTile(
                title    = tiles.getOrNull(0)?.title ?: "",
                imageUrl = tiles.getOrNull(0)?.imageUrl,
                modifier = Modifier.weight(1f),
                onClick  = { if (tiles.getOrNull(0) != null) onClick() }
            )
            ContentTile(
                title    = tiles.getOrNull(1)?.title ?: "",
                imageUrl = tiles.getOrNull(1)?.imageUrl,
                modifier = Modifier.weight(1f),
                onClick  = { if (tiles.getOrNull(1) != null) onClick() }
            )
        }
        // Row 2
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ContentTile(
                title    = tiles.getOrNull(2)?.title ?: "",
                imageUrl = tiles.getOrNull(2)?.imageUrl,
                modifier = Modifier.weight(1f),
                onClick  = { if (tiles.getOrNull(2) != null) onClick() }
            )
            if (tiles.size > 3) {
                ContentTile(
                    title    = tiles.getOrNull(3)?.title ?: "",
                    imageUrl = tiles.getOrNull(3)?.imageUrl,
                    modifier = Modifier.weight(1f),
                    onClick  = { onClick() }
                )
            } else {
                // Ghost tile to keep grid balanced on last page
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun FavoritesEmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier            = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "🎵", fontSize = 80.sp)
        Spacer(Modifier.height(16.dp))
        Text(
            text      = "Noch keine Lieblingssongs",
            style     = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text      = "Tippe auf das Herz bei einem Song!",
            style     = MaterialTheme.typography.bodyLarge,
            color     = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier  = Modifier.padding(horizontal = 32.dp)
        )
    }
}

// ── Previews ──────────────────────────────────────────────────────────────

@Preview(name = "BrowseScreen – Musik", showBackground = true, showSystemUi = true)
@Composable
private fun BrowseScreenMusicPreview() {
    KidstuneTheme {
        BrowseScreen(
            state = BrowseState(
                category = BrowseCategory.MUSIC,
                tiles    = MockContentProvider.mockMusicTiles,
                pages    = MockContentProvider.mockMusicTiles.chunked(4)
            )
        )
    }
}

@Preview(name = "BrowseScreen – Hörbücher", showBackground = true, showSystemUi = true)
@Composable
private fun BrowseScreenAudiobooksPreview() {
    KidstuneTheme {
        BrowseScreen(
            state = BrowseState(
                category = BrowseCategory.AUDIOBOOK,
                tiles    = MockContentProvider.mockAudiobookTiles,
                pages    = MockContentProvider.mockAudiobookTiles.chunked(4)
            )
        )
    }
}

@Preview(name = "BrowseScreen – Favoriten leer", showBackground = true, showSystemUi = true)
@Composable
private fun BrowseScreenFavoritesEmptyPreview() {
    KidstuneTheme {
        BrowseScreen(
            state = BrowseState(
                category = BrowseCategory.FAVORITES,
                tiles    = emptyList(),
                pages    = emptyList()
            )
        )
    }
}
