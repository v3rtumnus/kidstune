package at.kidstune.kids.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import at.kidstune.kids.domain.model.MockProfile
import at.kidstune.kids.ui.theme.KidstuneTheme
import at.kidstune.kids.ui.theme.MusicPrimary
import at.kidstune.kids.ui.viewmodel.PairingIntent
import at.kidstune.kids.ui.viewmodel.PairingState
import at.kidstune.kids.ui.viewmodel.PairingViewModel

// ── Stateful entry-point (used by NavHost) ────────────────────────────────────

@Composable
fun PairingScreen(
    modifier: Modifier = Modifier,
    viewModel: PairingViewModel = hiltViewModel(),
    onPairingSuccess: () -> Unit = {}
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state) {
        if (state is PairingState.Success) onPairingSuccess()
    }

    PairingScreen(
        modifier  = modifier,
        state     = state,
        onIntent  = viewModel::onIntent
    )
}

// ── Stateless composable (used in Previews and tests) ─────────────────────────

@Composable
fun PairingScreen(
    modifier: Modifier = Modifier,
    state: PairingState,
    onIntent: (PairingIntent) -> Unit = {}
) {
    val digits = when (state) {
        is PairingState.EnteringCode -> state.digits
        is PairingState.Error        -> state.digits
        PairingState.Confirming      -> emptyList()
        is PairingState.Success      -> emptyList()
    }
    val isLoading  = state is PairingState.Confirming
    val errorMsg   = (state as? PairingState.Error)?.message
    val canConnect = digits.size == 6 && !isLoading

    Surface(
        modifier = modifier.fillMaxSize(),
        color    = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier            = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // ── Title ─────────────────────────────────────────────────────────
            Text(
                text      = "Gerät verbinden",
                style     = MaterialTheme.typography.headlineLarge,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text      = "Code aus der Eltern-App eingeben",
                style     = MaterialTheme.typography.bodyLarge,
                color     = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(32.dp))

            // ── 6-digit display ───────────────────────────────────────────────
            DigitDisplay(digits = digits, hasError = errorMsg != null)

            // ── Error message ─────────────────────────────────────────────────
            Box(modifier = Modifier.height(32.dp), contentAlignment = Alignment.Center) {
                if (errorMsg != null) {
                    Text(
                        text      = errorMsg,
                        color     = MaterialTheme.colorScheme.error,
                        style     = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier  = Modifier
                            .fillMaxWidth()
                            .semantics { contentDescription = "Fehlermeldung: $errorMsg" }
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── Number pad ────────────────────────────────────────────────────
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(72.dp)
                        .semantics { contentDescription = "Verbindung wird hergestellt" }
                )
            } else {
                NumberPad(
                    onDigit     = { d -> onIntent(PairingIntent.DigitEntered(d)) },
                    onBackspace = { onIntent(PairingIntent.BackspacePressed) }
                )
            }

            Spacer(Modifier.height(24.dp))

            // ── Connect button ────────────────────────────────────────────────
            Button(
                onClick  = { onIntent(PairingIntent.ConnectPressed) },
                enabled  = canConnect,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .semantics { contentDescription = "Verbinden" }
            ) {
                Text(
                    text     = "Verbinden",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// ── DigitDisplay ──────────────────────────────────────────────────────────────

@Composable
private fun DigitDisplay(
    modifier: Modifier = Modifier,
    digits: List<Int>,
    hasError: Boolean
) {
    val borderColor = if (hasError) MaterialTheme.colorScheme.error else MusicPrimary

    Row(
        modifier              = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
    ) {
        repeat(6) { index ->
            val filled = index < digits.size
            Box(
                modifier          = Modifier
                    .size(48.dp)
                    .border(2.dp, borderColor, RoundedCornerShape(8.dp))
                    .background(
                        color = if (filled) borderColor.copy(alpha = 0.12f)
                                else Color.Transparent,
                        shape = RoundedCornerShape(8.dp)
                    ),
                contentAlignment  = Alignment.Center
            ) {
                if (filled) {
                    Text(
                        text      = digits[index].toString(),
                        fontSize  = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color     = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

// ── NumberPad ─────────────────────────────────────────────────────────────────

private val numPadLayout = listOf(
    listOf(1, 2, 3),
    listOf(4, 5, 6),
    listOf(7, 8, 9)
)

@Composable
private fun NumberPad(
    modifier: Modifier = Modifier,
    onDigit: (Int) -> Unit,
    onBackspace: () -> Unit
) {
    Column(
        modifier            = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        numPadLayout.forEach { row ->
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
            ) {
                row.forEach { digit ->
                    DigitButton(
                        label    = digit.toString(),
                        onClick  = { onDigit(digit) }
                    )
                }
            }
        }
        // Bottom row: empty | 0 | backspace
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
        ) {
            Spacer(Modifier.size(72.dp))
            DigitButton(label = "0", onClick = { onDigit(0) })
            FilledTonalButton(
                onClick  = onBackspace,
                modifier = Modifier
                    .size(72.dp)
                    .semantics { contentDescription = "Löschen" },
                shape    = CircleShape
            ) {
                Icon(
                    imageVector         = Icons.AutoMirrored.Filled.Backspace,
                    contentDescription  = null
                )
            }
        }
    }
}

@Composable
private fun DigitButton(
    modifier: Modifier = Modifier,
    label: String,
    onClick: () -> Unit
) {
    Button(
        onClick  = onClick,
        modifier = modifier
            .size(72.dp)
            .semantics { contentDescription = "Ziffer $label" },
        shape    = CircleShape
    ) {
        Text(
            text       = label,
            fontSize   = 24.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

// ── Previews ──────────────────────────────────────────────────────────────────

@Preview(name = "PairingScreen – empty", showBackground = true, showSystemUi = true)
@Composable
private fun PairingScreenEmptyPreview() {
    KidstuneTheme {
        PairingScreen(state = PairingState.EnteringCode())
    }
}

@Preview(name = "PairingScreen – 6 digits entered", showBackground = true, showSystemUi = true)
@Composable
private fun PairingScreenFilledPreview() {
    KidstuneTheme {
        PairingScreen(state = PairingState.EnteringCode(listOf(1, 2, 3, 4, 5, 6)))
    }
}

@Preview(name = "PairingScreen – loading", showBackground = true, showSystemUi = true)
@Composable
private fun PairingScreenLoadingPreview() {
    KidstuneTheme {
        PairingScreen(state = PairingState.Confirming)
    }
}

@Preview(name = "PairingScreen – error", showBackground = true, showSystemUi = true)
@Composable
private fun PairingScreenErrorPreview() {
    KidstuneTheme {
        PairingScreen(
            state = PairingState.Error(
                message = "Ungültiger Code",
                digits  = listOf(9, 9, 9, 9, 9, 9)
            )
        )
    }
}

@Preview(name = "PairingScreen – code expired", showBackground = true, showSystemUi = true)
@Composable
private fun PairingScreenExpiredPreview() {
    KidstuneTheme {
        PairingScreen(
            state = PairingState.Error(
                message = "Code abgelaufen – bitte einen neuen Code anfordern",
                digits  = listOf(1, 2, 3, 4, 5, 6)
            )
        )
    }
}
