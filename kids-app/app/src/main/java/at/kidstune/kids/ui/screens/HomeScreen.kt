package at.kidstune.kids.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import at.kidstune.kids.domain.model.ContentType
import at.kidstune.kids.ui.theme.KidstuneTheme

/**
 * Main home / browsing screen.
 * Full 2×2 grid implementation in Prompt 3.2.
 * Placeholder only.
 */
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    onNavigateToBrowse: (ContentType) -> Unit = {},
    onNavigateToNowPlaying: () -> Unit = {},
    onNavigateToDiscover: () -> Unit = {}
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color    = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier            = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
        ) {
            Text(
                text  = "Hallo!",
                style = MaterialTheme.typography.headlineLarge
            )
            Text(
                text  = "Home-Screen (Platzhalter)",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = { onNavigateToBrowse(ContentType.MUSIC) }) {
                Text("Musik")
            }
            Button(onClick = { onNavigateToBrowse(ContentType.AUDIOBOOK) }) {
                Text("Hörbücher")
            }
            Button(onClick = onNavigateToNowPlaying) {
                Text("Jetzt läuft")
            }
            Button(onClick = onNavigateToDiscover) {
                Text("Entdecken")
            }
        }
    }
}

@Preview(name = "HomeScreen", showBackground = true, showSystemUi = true)
@Composable
private fun HomeScreenPreview() {
    KidstuneTheme {
        HomeScreen()
    }
}
