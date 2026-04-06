package at.kidstune.kids.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import at.kidstune.kids.ui.theme.KidstuneTheme

/**
 * Shown on first launch when the device has no cached content yet.
 *
 * Conditions (evaluated in [HomeViewModel]):
 * - Profile is bound, but Room has zero [LocalContentEntry] rows for that profile.
 *
 * The screen is informational only. The user cannot navigate further until the
 * device connects to the internet and the background sync populates Room.
 * Once content arrives the Room [Flow] updates [HomeState.cachedContentCount]
 * and [HomeScreen] transitions back to the normal category grid automatically.
 */
@Composable
fun NoCacheScreen(modifier: Modifier = Modifier) {
    Column(
        modifier            = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp)
            .semantics { contentDescription = "Kein Cache verfügbar" },
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text     = "✈️",
            fontSize = 80.sp
        )

        Spacer(Modifier.height(24.dp))

        Text(
            text      = "Bitte mit WLAN verbinden",
            style     = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(12.dp))

        Text(
            text      = "Für den ersten Start wird eine Internetverbindung benötigt.",
            style     = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color     = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun NoCacheScreenPreview() {
    KidstuneTheme {
        NoCacheScreen()
    }
}
