package at.kidstune.kids.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.Color
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import at.kidstune.kids.data.local.entities.LocalContentEntry
import at.kidstune.kids.data.local.entities.LocalFavorite
import at.kidstune.kids.data.mock.MockContentProvider
import at.kidstune.kids.domain.model.BrowseCategory
import at.kidstune.kids.domain.model.ContentScope
import at.kidstune.kids.ui.components.ContentTile
import at.kidstune.kids.ui.components.PageIndicator
import at.kidstune.kids.ui.components.scopeBadgeFor
import at.kidstune.kids.ui.theme.KidstuneTheme
import at.kidstune.kids.ui.viewmodel.BrowseIntent
import at.kidstune.kids.ui.viewmodel.BrowseNavigation
import at.kidstune.kids.ui.viewmodel.BrowseSection
import at.kidstune.kids.ui.viewmodel.BrowseState
import at.kidstune.kids.ui.viewmodel.BrowseViewModel

// ── Stateful entry-point (used by NavHost) ────────────────────────────────

@Composable
fun BrowseScreen(
    modifier: Modifier = Modifier,
    category: BrowseCategory = BrowseCategory.MUSIC,
    viewModel: BrowseViewModel = hiltViewModel(),
    onNavigateUp: () -> Unit = {},
    onNavigateToAlbumGrid: (contentEntryId: String) -> Unit = {},
    onNavigateToTrackList: (albumId: String) -> Unit = {},
    onNavigateToPlaylistTrackList: (contentEntryId: String) -> Unit = {},
    onNavigateToNowPlaying: () -> Unit = {}
) {
    LaunchedEffect(category) { viewModel.init(category) }
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state.navigation) {
        when (val nav = state.navigation) {
            is BrowseNavigation.ToAlbumGrid -> {
                onNavigateToAlbumGrid(nav.contentEntryId)
                viewModel.onIntent(BrowseIntent.NavigationHandled)
            }
            is BrowseNavigation.ToTrackList -> {
                onNavigateToTrackList(nav.albumId)
                viewModel.onIntent(BrowseIntent.NavigationHandled)
            }
            is BrowseNavigation.ToPlaylistTrackList -> {
                onNavigateToPlaylistTrackList(nav.contentEntryId)
                viewModel.onIntent(BrowseIntent.NavigationHandled)
            }
            BrowseNavigation.ToNowPlaying -> {
                onNavigateToNowPlaying()
                viewModel.onIntent(BrowseIntent.NavigationHandled)
            }
            null -> Unit
        }
    }

    BrowseScreen(
        modifier     = modifier,
        state        = state,
        onIntent     = viewModel::onIntent,
        onNavigateUp = onNavigateUp,
    )
}

// ── Stateless composable (used in Previews and tests) ─────────────────────

@Composable
fun BrowseScreen(
    modifier: Modifier = Modifier,
    state: BrowseState,
    onIntent: (BrowseIntent) -> Unit = {},
    onNavigateUp: () -> Unit = {}
) {
    val title = when (state.category) {
        BrowseCategory.MUSIC     -> "Musik"
        BrowseCategory.FAVORITES -> "Lieblingssongs"
    }

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
                    text  = title,
                    style = MaterialTheme.typography.titleLarge
                )
            }
        }
    ) { innerPadding ->
        when (state.category) {
            BrowseCategory.MUSIC ->
                MusicContent(
                    sections = state.sections,
                    onIntent = onIntent,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                )

            BrowseCategory.FAVORITES ->
                FavoritesContent(
                    favorites      = state.favorites,
                    favoritesPages = state.favoritesPages,
                    onIntent       = onIntent,
                    modifier       = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                )
        }
    }
}

// ── Music: grouped sections ───────────────────────────────────────────────────

@Composable
private fun MusicContent(
    modifier: Modifier = Modifier,
    sections: List<BrowseSection>,
    onIntent: (BrowseIntent) -> Unit,
) {
    LazyColumn(
        modifier            = modifier.padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        sections.forEach { section ->
            item(key = "header_${section.scope.name}") {
                SectionHeader(scope = section.scope)
            }
            val rows = section.entries.chunked(2)
            items(rows, key = { row -> "row_${row.first().id}" }) { row ->
                Row(
                    modifier              = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    EntryTile(
                        entry    = row.getOrNull(0),
                        onIntent = onIntent,
                        modifier = Modifier.weight(1f).aspectRatio(1f)
                    )
                    EntryTile(
                        entry    = row.getOrNull(1),
                        onIntent = onIntent,
                        modifier = Modifier.weight(1f).aspectRatio(1f)
                    )
                }
            }
            item(key = "spacer_${section.scope.name}") {
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun SectionHeader(scope: ContentScope, modifier: Modifier = Modifier) {
    val label = when (scope) {
        ContentScope.ARTIST   -> "Künstler"
        ContentScope.ALBUM    -> "Alben"
        ContentScope.PLAYLIST -> "Playlists"
        ContentScope.TRACK    -> "Songs"
    }
    Text(
        text     = label,
        style    = MaterialTheme.typography.titleMedium,
        modifier = modifier.padding(top = 16.dp, bottom = 8.dp)
    )
}

// ── Favorites: paginated ──────────────────────────────────────────────────────

@Composable
private fun FavoritesContent(
    modifier: Modifier = Modifier,
    favorites: List<LocalFavorite>,
    favoritesPages: List<List<LocalFavorite>>,
    onIntent: (BrowseIntent) -> Unit,
) {
    when {
        favorites.isEmpty() -> FavoritesEmptyState(modifier = modifier)

        else -> {
            val pagerState = rememberPagerState { favoritesPages.size }
            Column(modifier = modifier) {
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
                        .semantics { testTag = "favorites_pager" }
                ) { page ->
                    FavoritesPage(
                        favorites = favoritesPages.getOrElse(page) { emptyList() },
                        onIntent  = onIntent
                    )
                }
                Box(
                    modifier         = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    PageIndicator(
                        pageCount   = favoritesPages.size,
                        currentPage = pagerState.currentPage
                    )
                }
            }
        }
    }
}

@Composable
private fun FavoritesPage(
    modifier: Modifier = Modifier,
    favorites: List<LocalFavorite>,
    onIntent: (BrowseIntent) -> Unit
) {
    val rows = favorites.chunked(2)
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
                FavoriteTile(
                    fav      = pair.getOrNull(0),
                    onIntent = onIntent,
                    modifier = Modifier.weight(1f).fillMaxSize()
                )
                FavoriteTile(
                    fav      = pair.getOrNull(1),
                    onIntent = onIntent,
                    modifier = Modifier.weight(1f).fillMaxSize()
                )
            }
        }
    }
}

// ── Tile composables ──────────────────────────────────────────────────────────

@Composable
private fun EntryTile(
    modifier: Modifier = Modifier,
    entry: LocalContentEntry?,
    onIntent: (BrowseIntent) -> Unit
) {
    if (entry == null) {
        Spacer(modifier = modifier)
        return
    }
    val badge = scopeBadgeFor(entry.scope.name)
    val cd = if (!entry.artistName.isNullOrBlank() && entry.artistName != entry.title)
        "${entry.title} von ${entry.artistName}"
    else
        entry.title
    ContentTile(
        modifier           = modifier,
        title              = entry.title,
        contentDescription = cd,
        imageUrl           = entry.imageUrl,
        scopeBadgeText     = badge?.first,
        scopeBadgeColor    = badge?.second ?: Color.Transparent,
        onClick            = { onIntent(BrowseIntent.TileTapped(entry)) }
    )
}

@Composable
private fun FavoriteTile(
    modifier: Modifier = Modifier,
    fav: LocalFavorite?,
    onIntent: (BrowseIntent) -> Unit
) {
    if (fav == null) {
        Spacer(modifier = modifier)
        return
    }
    val cd = if (!fav.artistName.isNullOrBlank() && fav.artistName != fav.title)
        "${fav.title} von ${fav.artistName}"
    else
        fav.title
    ContentTile(
        modifier           = modifier,
        title              = fav.title,
        contentDescription = cd,
        imageUrl           = fav.imageUrl,
        onClick            = { onIntent(BrowseIntent.FavoriteTapped(fav)) }
    )
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
    val entries = MockContentProvider.contentEntries.filter { it.contentType.name == "MUSIC" }
    val sections = listOf(ContentScope.ARTIST, ContentScope.ALBUM, ContentScope.PLAYLIST, ContentScope.TRACK)
        .mapNotNull { scope ->
            val group = entries.filter { it.scope == scope }
            if (group.isNotEmpty()) BrowseSection(scope, group) else null
        }
    KidstuneTheme {
        BrowseScreen(
            state = BrowseState(
                category = BrowseCategory.MUSIC,
                sections = sections
            )
        )
    }
}

@Preview(name = "BrowseScreen – Favoriten", showBackground = true, showSystemUi = true)
@Composable
private fun BrowseScreenFavoritesPreview() {
    KidstuneTheme {
        BrowseScreen(
            state = BrowseState(
                category       = BrowseCategory.FAVORITES,
                favorites      = MockContentProvider.favorites,
                favoritesPages = MockContentProvider.favorites.chunked(8)
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
                category       = BrowseCategory.FAVORITES,
                favorites      = emptyList(),
                favoritesPages = emptyList()
            )
        )
    }
}
