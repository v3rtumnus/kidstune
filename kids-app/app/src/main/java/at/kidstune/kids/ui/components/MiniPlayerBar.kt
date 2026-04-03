package at.kidstune.kids.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import at.kidstune.kids.ui.theme.KidstuneTheme
import at.kidstune.kids.ui.theme.kidsTouchTarget
import coil3.compose.AsyncImage

/**
 * Persistent mini-player bar shown at the bottom of every screen.
 * Tapping the bar expands to the NowPlaying screen.
 *
 * When nothing is playing ([title] is null) the bar is hidden.
 */
@Composable
fun MiniPlayerBar(
    modifier: Modifier = Modifier,
    title: String?,
    artistName: String?,
    imageUrl: String?,
    isPlaying: Boolean,
    onPlayPause: () -> Unit = {},
    onExpand: () -> Unit = {}
) {
    if (title == null) return

    Surface(
        modifier  = modifier.fillMaxWidth(),
        shadowElevation = 8.dp,
        color     = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onExpand)
                .semantics { contentDescription = "Jetzt läuft: $title" }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Thumbnail
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.primaryContainer)
            ) {
                AsyncImage(
                    model             = imageUrl,
                    contentDescription = null,
                    contentScale      = ContentScale.Crop,
                    modifier          = Modifier.size(52.dp)
                )
            }

            // Title + artist
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text     = title,
                    style    = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (artistName != null) {
                    Text(
                        text     = artistName,
                        style    = MaterialTheme.typography.bodySmall,
                        color    = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Play / Pause button – 72dp touch target
            IconButton(
                onClick  = onPlayPause,
                modifier = Modifier
                    .kidsTouchTarget()
                    .semantics { contentDescription = if (isPlaying) "Pause" else "Abspielen" }
            ) {
                Icon(
                    imageVector       = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint              = MaterialTheme.colorScheme.primary,
                    modifier          = Modifier.size(36.dp)
                )
            }
        }
    }
}

// ── Previews ──────────────────────────────────────────────────────────────

@Preview(name = "MiniPlayerBar – playing", showBackground = true)
@Composable
private fun MiniPlayerBarPlayingPreview() {
    KidstuneTheme {
        Column {
            MiniPlayerBar(
                title      = "Bibi & Tina – Folge 1 – Kapitel 3",
                artistName = "Bibi & Tina",
                imageUrl   = null,
                isPlaying  = true
            )
        }
    }
}

@Preview(name = "MiniPlayerBar – paused", showBackground = true)
@Composable
private fun MiniPlayerBarPausedPreview() {
    KidstuneTheme {
        Column {
            MiniPlayerBar(
                title      = "TKKG – Folge 200",
                artistName = "TKKG",
                imageUrl   = null,
                isPlaying  = false
            )
        }
    }
}

@Preview(name = "MiniPlayerBar – nothing playing (hidden)", showBackground = true)
@Composable
private fun MiniPlayerBarHiddenPreview() {
    KidstuneTheme {
        Column {
            MiniPlayerBar(
                title      = null,
                artistName = null,
                imageUrl   = null,
                isPlaying  = false
            )
        }
    }
}
