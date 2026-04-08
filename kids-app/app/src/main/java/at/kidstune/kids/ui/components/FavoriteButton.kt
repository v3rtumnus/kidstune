package at.kidstune.kids.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import at.kidstune.kids.ui.theme.FavoritePrimary
import at.kidstune.kids.ui.theme.KidstuneTheme
import at.kidstune.kids.ui.theme.kidsTouchTarget
import kotlinx.coroutines.launch

/**
 * Animated heart button.
 * - Tap → add to favorites: heart bounces in (1.0 → 1.3 → 1.0 with overshoot) and turns red.
 * - Long-press → remove from favorites: heart gently shrinks (1.0 → 0.7 → 1.0) and turns hollow.
 *
 * Touch target is always ≥72dp per design requirement.
 */
@Composable
fun FavoriteButton(
    modifier: Modifier = Modifier,
    isFavorite: Boolean,
    iconSize: Dp = 36.dp,
    onAdd: () -> Unit = {},
    onRemove: () -> Unit = {}
) {
    val color by animateColorAsState(
        targetValue   = if (isFavorite) FavoritePrimary else Color.White,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label         = "heart-color"
    )

    // Use Animatable for one-shot bounce/shrink on isFavorite transitions.
    val scale  = remember { Animatable(1f) }
    val scope  = rememberCoroutineScope()

    // Skip the very first composition so we don't bounce on initial render.
    var initialized by remember { mutableStateOf(false) }
    LaunchedEffect(isFavorite) {
        if (!initialized) {
            initialized = true
            return@LaunchedEffect
        }
        if (isFavorite) {
            // Bounce in: overshoot to 1.3×, spring back to 1.0
            scale.animateTo(
                1.3f,
                spring(dampingRatio = Spring.DampingRatioHighBouncy, stiffness = Spring.StiffnessMedium)
            )
            scale.animateTo(
                1.0f,
                spring(dampingRatio = Spring.DampingRatioLowBouncy,  stiffness = Spring.StiffnessMedium)
            )
        } else {
            // Gentle shrink: contract to 0.7×, ease back to 1.0
            scale.animateTo(
                0.7f,
                spring(stiffness = Spring.StiffnessMedium, dampingRatio = Spring.DampingRatioNoBouncy)
            )
            scale.animateTo(
                1.0f,
                spring(stiffness = Spring.StiffnessLow, dampingRatio = Spring.DampingRatioNoBouncy)
            )
        }
    }

    val haptic = LocalHapticFeedback.current

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .kidsTouchTarget()
            .semantics {
                contentDescription = if (isFavorite) "Aus Lieblingssongs entfernen" else "Zu Lieblingssongs hinzufügen"
                role = Role.Button
            }
            .pointerInput(isFavorite) {
                detectTapGestures(
                    onTap = {
                        if (!isFavorite) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onAdd()
                        }
                    },
                    onLongPress = {
                        if (isFavorite) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onRemove()
                        }
                    }
                )
            }
    ) {
        Icon(
            imageVector        = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
            contentDescription = null, // handled by parent semantics
            tint               = color,
            modifier           = Modifier
                .size(iconSize)
                .scale(scale.value)
        )
    }
}

// ── Previews ──────────────────────────────────────────────────────────────

@Preview(name = "FavoriteButton – inactive", showBackground = true, backgroundColor = 0xFF6750A4)
@Composable
private fun FavoriteButtonInactivePreview() {
    KidstuneTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            FavoriteButton(isFavorite = false)
        }
    }
}

@Preview(name = "FavoriteButton – active (favorited)", showBackground = true, backgroundColor = 0xFF6750A4)
@Composable
private fun FavoriteButtonActivePreview() {
    KidstuneTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            FavoriteButton(isFavorite = true)
        }
    }
}

@Preview(name = "FavoriteButton – large size", showBackground = true, backgroundColor = 0xFF333333)
@Composable
private fun FavoriteButtonLargePreview() {
    KidstuneTheme {
        var fav by remember { mutableStateOf(true) }
        Box(modifier = Modifier.padding(16.dp)) {
            FavoriteButton(isFavorite = fav, iconSize = 56.dp)
        }
    }
}
