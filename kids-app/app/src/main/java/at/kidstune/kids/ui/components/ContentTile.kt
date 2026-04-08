package at.kidstune.kids.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import at.kidstune.kids.ui.theme.KidstuneTheme
import at.kidstune.kids.ui.theme.ScopeBadgeAlbum
import at.kidstune.kids.ui.theme.ScopeBadgeArtist
import at.kidstune.kids.ui.theme.ScopeBadgePlaylist
import coil3.compose.AsyncImage

/**
 * Large square card showing album art that fills the entire card,
 * with the title overlaid at the bottom.
 *
 * Animations:
 * - **Shimmer** while the image loads.
 * - **Press scale** (0.95×) with spring release on tap.
 * - **Badge pulse** – the top-right badge oscillates in scale when present.
 * - **Haptic feedback** on tap.
 *
 * @param scopeBadgeText  Optional label shown in the bottom-left corner
 *   (e.g. "Künstler", "Album", "Playlist"). Pass `null` to omit.
 * @param scopeBadgeColor Background color for the scope badge pill.
 * @param badgeText       Optional label shown in the top-right corner (e.g. "NEU").
 */
@Composable
fun ContentTile(
    modifier: Modifier = Modifier,
    title: String,
    imageUrl: String?,
    contentDescription: String = title,
    scopeBadgeText: String? = null,
    scopeBadgeColor: Color = ScopeBadgeArtist,
    badgeText: String? = null,
    onClick: () -> Unit = {}
) {
    // ── Press-scale animation ─────────────────────────────────────────────
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue   = if (isPressed) 0.95f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label         = "tile-press-scale"
    )
    val haptic = LocalHapticFeedback.current

    // ── Shimmer state ─────────────────────────────────────────────────────
    // Start shimmering whenever there is a URL to fetch.
    var isLoading by remember(imageUrl) { mutableStateOf(imageUrl != null) }

    Card(
        modifier = modifier
            .aspectRatio(1f)
            .scale(pressScale)
            .clickable(
                interactionSource = interactionSource,
                indication        = LocalIndication.current,
                onClick           = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick()
                }
            )
            .semantics { this.contentDescription = contentDescription },
        shape     = MaterialTheme.shapes.medium,
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // ── Shimmer placeholder ──────────────────────────────────────
            if (isLoading) {
                ShimmerBox(modifier = Modifier.fillMaxSize())
            }

            // ── Cover art ────────────────────────────────────────────────
            AsyncImage(
                model              = imageUrl,
                contentDescription = null, // handled by card semantics
                contentScale       = ContentScale.Crop,
                onLoading          = { isLoading = true },
                onSuccess          = { isLoading = false },
                onError            = { isLoading = false },
                modifier           = Modifier.fillMaxSize()
            )

            // ── Gradient scrim + title ────────────────────────────────────
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

            // ── Scope badge – bottom-left ──────────────────────────────────
            if (scopeBadgeText != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 6.dp, bottom = 36.dp)
                        .clip(MaterialTheme.shapes.extraSmall)
                        .background(scopeBadgeColor)
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Text(
                        text  = scopeBadgeText,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White
                    )
                }
            }

            // ── Top-right badge (e.g. "NEU") with pulse animation ──────────
            if (badgeText != null) {
                val infiniteTransition = rememberInfiniteTransition(label = "badge-pulse")
                val badgeScale by infiniteTransition.animateFloat(
                    initialValue  = 1.00f,
                    targetValue   = 1.20f,
                    animationSpec = infiniteRepeatable(
                        animation  = tween(500, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "badge-scale"
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .scale(badgeScale)
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

// ── Helpers ───────────────────────────────────────────────────────────────

/** Returns the scope badge text and color for the given content scope name. */
fun scopeBadgeFor(scope: String): Pair<String, Color>? = when (scope) {
    "ARTIST"   -> "Künstler" to ScopeBadgeArtist
    "ALBUM"    -> "Album"    to ScopeBadgeAlbum
    "PLAYLIST" -> "Playlist" to ScopeBadgePlaylist
    else       -> null  // TRACK – no badge
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

@Preview(name = "ContentTile – Artist scope badge", showBackground = true)
@Composable
private fun ContentTileArtistBadgePreview() {
    KidstuneTheme {
        Column(modifier = Modifier.padding(16.dp)) {
            ContentTile(
                title           = "Bibi & Tina",
                imageUrl        = null,
                scopeBadgeText  = "Künstler",
                scopeBadgeColor = ScopeBadgeArtist,
            )
        }
    }
}

@Preview(name = "ContentTile – top-right badge", showBackground = true)
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
