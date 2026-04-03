package at.kidstune.kids.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import at.kidstune.kids.domain.model.ContentType
import at.kidstune.kids.ui.theme.KidstuneTheme

/**
 * Browse screen showing all content of a given [ContentType].
 * Full paginated grid implementation in Prompt 3.2.
 * Placeholder only.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseScreen(
    modifier: Modifier = Modifier,
    contentType: ContentType = ContentType.MUSIC,
    onNavigateUp: () -> Unit = {},
    onNavigateToNowPlaying: () -> Unit = {}
) {
    val title = when (contentType) {
        ContentType.MUSIC     -> "Musik"
        ContentType.AUDIOBOOK -> "Hörbücher"
    }

    Scaffold(
        modifier = modifier,
        topBar   = {
            TopAppBar(
                title           = { Text(title) },
                navigationIcon  = {
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
        Column(
            modifier            = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text  = "$title – Raster (Platzhalter)",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Preview(name = "BrowseScreen – Music", showBackground = true, showSystemUi = true)
@Composable
private fun BrowseScreenMusicPreview() {
    KidstuneTheme {
        BrowseScreen(contentType = ContentType.MUSIC)
    }
}

@Preview(name = "BrowseScreen – Audiobooks", showBackground = true, showSystemUi = true)
@Composable
private fun BrowseScreenAudiobooksPreview() {
    KidstuneTheme {
        BrowseScreen(contentType = ContentType.AUDIOBOOK)
    }
}
