package at.kidstune.kids.ui.screens

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import at.kidstune.kids.data.mock.MockDiscoverData
import at.kidstune.kids.domain.model.ContentScope
import at.kidstune.kids.domain.model.DiscoverTile
import at.kidstune.kids.domain.model.PendingRequest
import at.kidstune.kids.domain.model.RequestStatus
import at.kidstune.kids.ui.components.ConfettiCanvas
import at.kidstune.kids.ui.components.ContentTile
import at.kidstune.kids.ui.components.scopeBadgeFor
import at.kidstune.kids.ui.theme.KidstuneTheme
import at.kidstune.kids.ui.theme.kidsTouchTarget
import at.kidstune.kids.ui.viewmodel.DiscoverIntent
import at.kidstune.kids.ui.viewmodel.DiscoverState
import at.kidstune.kids.ui.viewmodel.DiscoverViewModel
import at.kidstune.kids.ui.viewmodel.pendingRequestTimeLabel
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.delay

// ── Stateful entry-point (used by NavHost) ────────────────────────────────

@Composable
fun DiscoverScreen(
    modifier: Modifier = Modifier,
    viewModel: DiscoverViewModel = hiltViewModel(),
    onNavigateUp: () -> Unit = {}
) {
    val state by viewModel.state.collectAsState()

    // Wire mic button: launch SpeechRecognizer and forward result as VoiceInputResult intent
    val speechLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val text = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
            if (!text.isNullOrBlank()) {
                viewModel.onIntent(DiscoverIntent.VoiceInputResult(text))
            }
        }
    }

    DiscoverScreen(
        modifier      = modifier,
        state         = state,
        onIntent      = viewModel::onIntent,
        onNavigateUp  = onNavigateUp,
        onMicClick    = {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "de-DE")
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Was suchst du?")
            }
            try { speechLauncher.launch(intent) } catch (_: Exception) { /* not available */ }
        }
    )
}

// ── Stateless composable (used in Previews and tests) ─────────────────────

@Composable
fun DiscoverScreen(
    modifier: Modifier = Modifier,
    state: DiscoverState,
    onIntent: (DiscoverIntent) -> Unit = {},
    onNavigateUp: () -> Unit = {},
    onMicClick: () -> Unit = {},
) {
    val tiles         = if (state.query.isEmpty()) state.suggestions else state.searchResults
    val pendingCount  = state.pendingRequests.count { it.status == RequestStatus.PENDING }
    val isLimitReached = pendingCount >= 3

    Box(modifier = modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
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
                        onChange = { onIntent(DiscoverIntent.UpdateQuery(it)) },
                        onMicClick = onMicClick,
                    )
                }

                // ── Rate-limit message ────────────────────────────────────────
                if (state.rateLimitMessage != null) {
                    item {
                        Text(
                            text     = state.rateLimitMessage,
                            style    = MaterialTheme.typography.bodySmall,
                            color    = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                        )
                    }
                }

                // ── Tile grid (2-column) ─────────────────────────────────────
                val rows = tiles.chunked(2)
                items(rows, key = { row -> row.first().spotifyUri }) { row ->
                    DiscoverTileRow(
                        tiles          = row,
                        requestedUris  = state.requestedUris,
                        newlyApproved  = state.newlyApprovedUris,
                        isLimitReached = isLimitReached,
                        pinAvailable   = state.pinAvailable,
                        onRequest      = { tile ->
                            if (state.pinAvailable) {
                                onIntent(DiscoverIntent.OpenApprovalChoice(tile))
                            } else {
                                onIntent(DiscoverIntent.RequestContent(tile))
                            }
                        }
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

        // ── Celebration overlay ───────────────────────────────────────────────
        AnimatedVisibility(
            visible  = state.showCelebration,
            enter    = fadeIn(),
            exit     = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                ConfettiCanvas(modifier = Modifier.fillMaxSize())
                CelebrationOverlay(
                    modifier  = Modifier.align(Alignment.Center),
                    onDismiss = { onIntent(DiscoverIntent.DismissCelebration) }
                )
            }
        }

        // ── Approval-choice overlay ─────────────────────────────────────��─────
        AnimatedVisibility(
            visible  = state.showApprovalChoice,
            enter    = fadeIn() + scaleIn(initialScale = 0.92f),
            exit     = fadeOut() + scaleOut(targetScale = 0.92f),
            modifier = Modifier.fillMaxSize()
        ) {
            ApprovalChoiceOverlay(
                tile     = state.pinFlowTile,
                onSendRequest = { onIntent(DiscoverIntent.SendRequestFromChoice) },
                onAskParent   = { onIntent(DiscoverIntent.OpenPinPad) },
                onCancel      = { onIntent(DiscoverIntent.PinCancel) },
                modifier = Modifier.fillMaxSize()
            )
        }

        // ── PIN-pad overlay ───────────────────────────────────────────────────
        AnimatedVisibility(
            visible  = state.showPinPad,
            enter    = fadeIn() + scaleIn(initialScale = 0.92f),
            exit     = fadeOut() + scaleOut(targetScale = 0.92f),
            modifier = Modifier.fillMaxSize()
        ) {
            PinPadOverlay(
                tile               = state.pinFlowTile,
                digits             = state.pinDigits,
                isError            = state.pinError,
                isTooManyAttempts  = state.pinTooManyAttempts,
                isSubmitting       = state.pinSubmitting,
                onDigit            = { onIntent(DiscoverIntent.PinDigit(it)) },
                onBackspace        = { onIntent(DiscoverIntent.PinBackspace) },
                onCancel           = { onIntent(DiscoverIntent.PinCancel) },
                onTimeout          = { onIntent(DiscoverIntent.PinTimeout) },
                onRetryAsRequest   = { onIntent(DiscoverIntent.RetryAsRequest) },
                modifier           = Modifier.fillMaxSize()
            )
        }
    }
}

// ── Private composables ───────────────────────────────────────────────────

@Composable
private fun DiscoverSearchField(
    modifier: Modifier = Modifier,
    query: String,
    onChange: (String) -> Unit,
    onMicClick: () -> Unit = {},
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
                onClick  = onMicClick,
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
    newlyApproved: Set<String>,
    isLimitReached: Boolean,
    pinAvailable: Boolean,
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
                tile            = tile,
                isRequested     = tile.spotifyUri in requestedUris,
                isLimitReached  = isLimitReached,
                isNewlyApproved = tile.spotifyUri in newlyApproved,
                pinAvailable    = pinAvailable,
                onRequest       = { onRequest(tile) },
                modifier        = Modifier.weight(1f)
            )
        }
        if (tiles.size == 1) Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun DiscoverTileCard(
    modifier: Modifier = Modifier,
    tile: DiscoverTile,
    isRequested: Boolean,
    isLimitReached: Boolean,
    isNewlyApproved: Boolean,
    pinAvailable: Boolean,
    onRequest: () -> Unit
) {
    val badge = scopeBadgeFor(tile.scope)

    Column(modifier = modifier) {
        ContentTile(
            title              = tile.title,
            contentDescription = "${tile.title} von ${tile.artistName}",
            imageUrl           = tile.imageUrl,
            scopeBadgeText     = badge?.first,
            scopeBadgeColor    = badge?.second ?: Color.Transparent,
            badgeText          = if (isNewlyApproved) "NEU" else null,
            onClick            = {}
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
            pinAvailable -> {
                Button(
                    onClick  = onRequest,
                    modifier = Modifier
                        .fillMaxWidth()
                        .kidsTouchTarget()
                        .semantics { contentDescription = "Freigabe: ${tile.title}" }
                ) {
                    Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("🙏 Ich will das!")
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

@Composable
private fun CelebrationOverlay(
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit
) {
    LaunchedEffect(Unit) {
        delay(3_000)
        onDismiss()
    }

    Card(
        modifier = modifier.padding(32.dp)
    ) {
        Column(
            modifier              = Modifier.padding(24.dp),
            horizontalAlignment   = Alignment.CenterHorizontally,
            verticalArrangement   = Arrangement.spacedBy(8.dp)
        ) {
            Text("🎉", fontSize = 48.sp)
            Text(
                text  = "Mama/Papa haben deinen Wunsch genehmigt!",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text  = "Du findest deine neue Musik in den Tabs!",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ── Approval-choice overlay ───────────────────────────────────────────────

@Composable
private fun ApprovalChoiceOverlay(
    modifier: Modifier = Modifier,
    tile: DiscoverTile?,
    onSendRequest: () -> Unit,
    onAskParent: () -> Unit,
    onCancel: () -> Unit,
) {
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f)),
        contentAlignment = Alignment.Center
    ) {
        Card(modifier = Modifier.padding(24.dp)) {
            Column(
                modifier            = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("🎵", fontSize = 36.sp)
                tile?.let {
                    Text(
                        text      = it.title,
                        style     = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center
                    )
                }
                Text(
                    text      = "Wie soll das hinzugefügt werden?",
                    style     = MaterialTheme.typography.bodyMedium,
                    color     = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                // Option 1: normal request
                FilledTonalButton(
                    onClick  = onSendRequest,
                    modifier = Modifier
                        .fillMaxWidth()
                        .kidsTouchTarget()
                ) {
                    Icon(Icons.Filled.Send, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("Anfrage senden", fontWeight = FontWeight.Bold)
                        Text(
                            text  = "Mama/Papa bekommt eine Nachricht",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }

                // Option 2: PIN approval
                Button(
                    onClick  = onAskParent,
                    modifier = Modifier
                        .fillMaxWidth()
                        .kidsTouchTarget()
                ) {
                    Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("Elternteil fragen", fontWeight = FontWeight.Bold)
                        Text(
                            text  = "Gib das Handy an Mama oder Papa",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }

                OutlinedButton(
                    onClick  = onCancel,
                    modifier = Modifier.kidsTouchTarget()
                ) {
                    Text("Abbrechen")
                }
            }
        }
    }
}

// ── PIN-pad overlay ───────────────────────────────────────────────────────

@Composable
private fun PinPadOverlay(
    modifier: Modifier = Modifier,
    tile: DiscoverTile?,
    digits: String,
    isError: Boolean,
    isTooManyAttempts: Boolean,
    isSubmitting: Boolean,
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    onCancel: () -> Unit,
    onTimeout: () -> Unit,
    onRetryAsRequest: () -> Unit,
) {
    // 30-second inactivity timeout
    LaunchedEffect(Unit) {
        delay(30_000)
        onTimeout()
    }

    Box(
        modifier         = modifier.background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier            = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text("👨‍👩‍👧", fontSize = 48.sp)

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text      = "Gib das Handy an Mama oder Papa!",
                    style     = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                tile?.let {
                    Text(
                        text      = "Freigabe für: ${it.title}",
                        style     = MaterialTheme.typography.bodyMedium,
                        color     = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Dot indicators
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                repeat(4) { index ->
                    val filled = index < digits.length
                    val color  = when {
                        isError  -> MaterialTheme.colorScheme.error
                        filled   -> MaterialTheme.colorScheme.primary
                        else     -> MaterialTheme.colorScheme.outlineVariant
                    }
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .background(color, shape = MaterialTheme.shapes.extraLarge)
                    )
                }
            }

            // Error / lockout feedback
            when {
                isTooManyAttempts -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text      = "Zu viele Fehlversuche.\nBitte in 10 Minuten erneut versuchen.",
                            color     = MaterialTheme.colorScheme.error,
                            style     = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )
                        Button(
                            onClick  = onRetryAsRequest,
                            modifier = Modifier.fillMaxWidth().kidsTouchTarget(),
                            colors   = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondary
                            )
                        ) {
                            Icon(Icons.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Stattdessen Anfrage senden")
                        }
                    }
                }
                isError -> Text(
                    text  = "Falscher Code – bitte erneut versuchen.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            // Numpad
            if (!isTooManyAttempts) {
                if (isSubmitting) {
                    CircularProgressIndicator()
                } else {
                    NumPad(
                        onDigit     = onDigit,
                        onBackspace = onBackspace,
                        enabled     = !isSubmitting
                    )
                }
            }

            OutlinedButton(
                onClick  = onCancel,
                modifier = Modifier.kidsTouchTarget()
            ) {
                Icon(
                    imageVector        = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = null,
                    modifier           = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text("Zurück")
            }
        }
    }
}

@Composable
private fun NumPad(
    modifier: Modifier = Modifier,
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    enabled: Boolean = true
) {
    val rows = listOf(
        listOf('1', '2', '3'),
        listOf('4', '5', '6'),
        listOf('7', '8', '9'),
    )

    Column(
        modifier            = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { digit ->
                    NumPadKey(
                        label   = digit.toString(),
                        onClick = { onDigit(digit) },
                        enabled = enabled
                    )
                }
            }
        }
        // Bottom row: empty slot + 0 + backspace
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Spacer(Modifier.size(96.dp))
            NumPadKey(label = "0", onClick = { onDigit('0') }, enabled = enabled)
            IconButton(
                onClick  = onBackspace,
                enabled  = enabled,
                modifier = Modifier.size(96.dp)
            ) {
                Icon(
                    imageVector        = Icons.Filled.Backspace,
                    contentDescription = "Löschen",
                    modifier           = Modifier.size(32.dp)
                )
            }
        }
    }
}

@Composable
private fun NumPadKey(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean
) {
    FilledTonalButton(
        onClick  = onClick,
        enabled  = enabled,
        modifier = Modifier.size(96.dp)
    ) {
        Text(
            text      = label,
            fontSize  = 28.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/** Maps a [ContentScope] to the badge label and color used in [ContentTile]. */
fun scopeBadgeFor(scope: ContentScope): Pair<String, Color>? =
    at.kidstune.kids.ui.components.scopeBadgeFor(scope.name)

// ── Previews ──────────────────────────────────────────────────────────────

@Preview(name = "Discover – Idle", showBackground = true, showSystemUi = true)
@Composable
private fun DiscoverIdlePreview() {
    KidstuneTheme {
        DiscoverScreen(state = DiscoverState(suggestions = MockDiscoverData.mockSuggestions))
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
                suggestions     = MockDiscoverData.mockSuggestions,
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
                suggestions     = MockDiscoverData.mockSuggestions,
                pendingRequests = threePending,
                requestedUris   = threePending.map { it.tile.spotifyUri }.toSet()
            )
        )
    }
}

@Preview(name = "Discover – Celebration", showBackground = true, showSystemUi = true)
@Composable
private fun DiscoverCelebrationPreview() {
    KidstuneTheme {
        DiscoverScreen(
            state = DiscoverState(
                suggestions     = MockDiscoverData.mockSuggestions,
                showCelebration = true,
            )
        )
    }
}

@Preview(name = "Discover – Approval Choice", showBackground = true, showSystemUi = true)
@Composable
private fun DiscoverApprovalChoicePreview() {
    KidstuneTheme {
        DiscoverScreen(
            state = DiscoverState(
                suggestions        = MockDiscoverData.mockSuggestions,
                pinAvailable       = true,
                showApprovalChoice = true,
                pinFlowTile        = MockDiscoverData.mockSuggestions.firstOrNull(),
            )
        )
    }
}

@Preview(name = "Discover – PIN Pad", showBackground = true, showSystemUi = true)
@Composable
private fun DiscoverPinPadPreview() {
    KidstuneTheme {
        DiscoverScreen(
            state = DiscoverState(
                suggestions  = MockDiscoverData.mockSuggestions,
                pinAvailable = true,
                showPinPad   = true,
                pinFlowTile  = MockDiscoverData.mockSuggestions.firstOrNull(),
                pinDigits    = "12",
            )
        )
    }
}

@Preview(name = "Discover – PIN Error", showBackground = true, showSystemUi = true)
@Composable
private fun DiscoverPinErrorPreview() {
    KidstuneTheme {
        DiscoverScreen(
            state = DiscoverState(
                pinAvailable = true,
                showPinPad   = true,
                pinFlowTile  = MockDiscoverData.mockSuggestions.firstOrNull(),
                pinError     = true,
            )
        )
    }
}

@Preview(name = "Discover – PIN Lockout", showBackground = true, showSystemUi = true)
@Composable
private fun DiscoverPinLockoutPreview() {
    KidstuneTheme {
        DiscoverScreen(
            state = DiscoverState(
                pinAvailable       = true,
                showPinPad         = true,
                pinFlowTile        = MockDiscoverData.mockSuggestions.firstOrNull(),
                pinTooManyAttempts = true,
            )
        )
    }
}
