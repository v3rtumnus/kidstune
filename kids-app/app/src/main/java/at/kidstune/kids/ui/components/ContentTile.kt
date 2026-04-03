package at.kidstune.kids.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import at.kidstune.kids.ui.theme.KidstuneTheme
import coil3.compose.AsyncImage

/**
 * Large square card showing album art that fills the entire card,
 * with the title overlaid at the bottom and an optional badge.
 * Used in the 2×2 browsing grid.
 */
@Composable
fun ContentTile(
    modifier: Modifier = Modifier,
    title: String,
    imageUrl: String?,
    contentDescription: String = title,
    badgeText: String? = null,
    onClick: () -> Unit = {}
) {
    Card(
        modifier = modifier
            .aspectRatio(1f)
            .clickable(onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        shape    = MaterialTheme.shapes.medium,
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Cover art fills the entire tile
            AsyncImage(
                model             = imageUrl,
                contentDescription = null, // handled by card semantics above
                contentScale      = ContentScale.Crop,
                modifier          = Modifier.fillMaxSize()
            )

            // Gradient scrim at the bottom for text readability
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color(0xCC000000))
                        )
                    )
                    .padding(horizontal = 8.dp, vertical = 10.dp)
            ) {
                Text(
                    text     = title,
                    style    = MaterialTheme.typography.titleSmall,
                    color    = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Optional badge (e.g., "NEU", content type label)
            if (badgeText != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .clip(MaterialTheme.shapes.extraSmall)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text  = badgeText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}

// ── Previews ──────────────────────────────────────────────────────────────

@Preview(name = "ContentTile – with image URL", showBackground = true)
@Composable
private fun ContentTilePreview() {
    KidstuneTheme {
        Column(modifier = Modifier.padding(16.dp)) {
            ContentTile(
                title    = "Bibi & Tina – Folge 1",
                imageUrl = "https://picsum.photos/seed/bibi/300/300",
            )
        }
    }
}

@Preview(name = "ContentTile – no image", showBackground = true)
@Composable
private fun ContentTileNoImagePreview() {
    KidstuneTheme {
        Column(modifier = Modifier.padding(16.dp)) {
            ContentTile(
                title    = "Hörspiel ohne Cover",
                imageUrl = null,
            )
        }
    }
}

@Preview(name = "ContentTile – with badge", showBackground = true)
@Composable
private fun ContentTileBadgePreview() {
    KidstuneTheme {
        Column(modifier = Modifier.padding(16.dp)) {
            ContentTile(
                title     = "TKKG – Folge 200",
                imageUrl  = null,
                badgeText = "NEU",
            )
        }
    }
}
