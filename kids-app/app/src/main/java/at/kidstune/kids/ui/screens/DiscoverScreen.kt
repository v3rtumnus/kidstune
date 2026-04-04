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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import at.kidstune.kids.data.mock.MockDiscoverData
import at.kidstune.kids.domain.model.DiscoverTile
import at.kidstune.kids.domain.model.PendingRequest
import at.kidstune.kids.domain.model.RequestStatus
import at.kidstune.kids.ui.components.ContentTile
import at.kidstune.kids.ui.theme.KidstuneTheme
import at.kidstune.kids.ui.theme.kidsTouchTarget
import at.kidstune.kids.ui.viewmodel.DiscoverIntent
import at.kidstune.kids.ui.viewmodel.DiscoverState
import at.kidstune.kids.ui.viewmodel.DiscoverViewModel
import at.kidstune.kids.ui.viewmodel.pendingRequestTimeLabel
import java.time.Instant
import java.time.temporal.ChronoUnit

// ── Stateful entry-point (used by NavHost) ────────────────────────────────

@Composable
fun DiscoverScreen(
    modifier: Modifier = Modifier,
    viewModel: DiscoverViewModel = viewModel(),
    onNavigateUp: () -> Unit = {}
) {
    val state by viewModel.state.collectAsState()
    DiscoverScreen(
        modifier     = modifier,
        state        = state,
        onIntent     = viewModel::onIntent,
        onNavigateUp = onNavigateUp
    )
}

// ── Stateless composable (used in Previews and tests) ─────────────────────

@Composable
fun DiscoverScreen(
    modifier: Modifier = Modifier,
    state: DiscoverState,
    onIntent: (DiscoverIntent) -> Unit = {},
    onNavigateUp: () -> Unit = {}
) {
    val tiles         = if (state.query.isEmpty()) state.suggestions else state.searchResults
    val pendingCount  = state.pendingRequests.count { it.status == RequestStatus.PENDING }
    val isLimitReached = pendingCount >= 3

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
                    text  = "Entdecken",
                    style = MaterialTheme.typography.titleLarge
                )
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier            = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .semantics { testTag = "discover_lazy_column" },
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            // ── Search field ─────────────────────────────────────────────
            item {
                DiscoverSearchField(
                    query    = state.query,
                    onChange = { onIntent(DiscoverIntent.UpdateQuery(it)) }
                )
            }

            // ── Tile grid (2-column) ─────────────────────────────────────
            val rows = tiles.chunked(2)
            items(rows, key = { row -> row.first().spotifyUri }) { row ->
                DiscoverTileRow(
                    tiles          = row,
                    requestedUris  = state.requestedUris,
                    isLimitReached = isLimitReached,
                    onRequest      = { onIntent(DiscoverIntent.RequestContent(it)) }
                )
            }

            // ── Meine Wünsche ─────────────────────────────────────────────
            if (state.pendingRequests.isNotEmpty()) {
                item {
                    Text(
                        text     = "Meine Wünsche",
                        style    = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 8.dp)
                    )
                }
                items(state.pendingRequests, key = { it.id }) { request ->
                    PendingRequestCard(
                        request   = request,
                        onDismiss = { onIntent(DiscoverIntent.DismissRejected(it)) }
                    )
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

// ── Private composables ───────────────────────────────────────────────────

@Composable
private fun DiscoverSearchField(
    modifier: Modifier = Modifier,
    query: String,
    onChange: (String) -> Unit
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier          = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector        = Icons.Filled.Search,
                contentDescription = null,
                tint               = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(8.dp))
            Box(modifier = Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text(
                        text     = "Suchen...",
                        fontSize = 20.sp,
                        color    = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                BasicTextField(
                    value         = query,
                    onValueChange = onChange,
                    textStyle     = TextStyle(
                        fontSize = 20.sp,
                        color    = MaterialTheme.colorScheme.onSurface
                    ),
                    singleLine    = true,
                    modifier      = Modifier
                        .fillMaxWidth()
                        .semantics { testTag = "discover_search_field" }
                )
            }
            IconButton(
                onClick  = {},   // mic – no-op for now
                modifier = Modifier.kidsTouchTarget()
            ) {
                Icon(
                    imageVector        = Icons.Filled.Mic,
                    contentDescription = "Spracheingabe"
                )
            }
        }
    }
}

@Composable
private fun DiscoverTileRow(
    modifier: Modifier = Modifier,
    tiles: List<DiscoverTile>,
    requestedUris: Set<String>,
    isLimitReached: Boolean,
    onRequest: (DiscoverTile) -> Unit
) {
    Row(
        modifier              = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        tiles.forEach { tile ->
            DiscoverTileCard(
                tile           = tile,
                isRequested    = tile.spotifyUri in requestedUris,
                isLimitReached = isLimitReached,
                onRequest      = { onRequest(tile) },
                modifier       = Modifier.weight(1f)
            )
        }
        // Ghost column to keep grid balanced when row has only 1 tile
        if (tiles.size == 1) {
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun DiscoverTileCard(
    modifier: Modifier = Modifier,
    tile: DiscoverTile,
    isRequested: Boolean,
    isLimitReached: Boolean,
    onRequest: () -> Unit
) {
    Column(modifier = modifier) {
        ContentTile(
            title    = tile.title,
            imageUrl = tile.imageUrl,
            onClick  = {}
        )
        Spacer(Modifier.height(6.dp))
        when {
            isRequested -> {
                Button(
                    onClick  = {},
                    enabled  = false,
                    modifier = Modifier
                        .fillMaxWidth()
                        .kidsTouchTarget()
                ) {
                    Text("Angefragt")
                }
            }
            isLimitReached -> {
                Button(
                    onClick  = {},
                    enabled  = false,
                    modifier = Modifier
                        .fillMaxWidth()
                        .kidsTouchTarget()
                ) {
                    Text(
                        text  = "Du hast schon 3 Wünsche offen – warte bis Mama/Papa geantwortet hat!",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
            else -> {
                Button(
                    onClick  = onRequest,
                    modifier = Modifier
                        .fillMaxWidth()
                        .kidsTouchTarget()
                        .semantics { contentDescription = "Wünschen: ${tile.title}" }
                ) {
                    Text("🙏 Ich will das!")
                }
            }
        }
    }
}

@Composable
private fun PendingRequestCard(
    modifier: Modifier = Modifier,
    request: PendingRequest,
    onDismiss: (String) -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Row(
            modifier              = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (request.status == RequestStatus.PENDING) {
                Icon(
                    imageVector        = Icons.Filled.Schedule,
                    contentDescription = null,
                    tint               = MaterialTheme.colorScheme.primary
                )
            } else {
                Text("❌", fontSize = 20.sp)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text  = request.tile.title,
                    style = MaterialTheme.typography.bodyMedium
                )
                if (request.status == RequestStatus.PENDING) {
                    Text(
                        text  = pendingRequestTimeLabel(request.requestedAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else if (request.parentNote != null) {
                    Text(
                        text  = request.parentNote,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// ── Previews ──────────────────────────────────────────────────────────────

@Preview(name = "Discover – Idle", showBackground = true, showSystemUi = true)
@Composable
private fun DiscoverIdlePreview() {
    KidstuneTheme {
        DiscoverScreen(state = DiscoverState())
    }
}

@Preview(name = "Discover – Search Active", showBackground = true, showSystemUi = true)
@Composable
private fun DiscoverSearchPreview() {
    KidstuneTheme {
        DiscoverScreen(
            state = DiscoverState(
                query         = "Frozen",
                searchResults = MockDiscoverData.mockSearchResults
            )
        )
    }
}

@Preview(name = "Discover – With Pending", showBackground = true, showSystemUi = true)
@Composable
private fun DiscoverWithPendingPreview() {
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

@Preview(name = "Discover – Limit Reached", showBackground = true, showSystemUi = true)
@Composable
private fun DiscoverLimitReachedPreview() {
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
