package at.kidstune.kids.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import at.kidstune.kids.ui.theme.KidstuneTheme

private val LockAmber      = Color(0xFFFFC107)
private val LockAmberDark  = Color(0xFFF57F17)
private val LockShade      = Color(0xFFFF8F00)

/**
 * Shown when [at.kidstune.kids.playback.SpotifyConnectionError.NOT_LOGGED_IN] is detected.
 *
 * The parent needs to log in to the Spotify app on the device.
 */
@Composable
fun SpotifyNotLoggedInScreen(modifier: Modifier = Modifier) {
    KidsErrorScreen(
        modifier              = modifier,
        contentDescriptionTag = "Spotify nicht angemeldet",
        illustration          = { LockIllustration() },
        title                 = "Spotify ist nicht angemeldet.",
        subtitle              = "Bitte Mama oder Papa fragen."
    )
}

@Composable
private fun LockIllustration(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(160.dp)) {
        val w = size.width
        val h = size.height

        val bodyLeft   = w * 0.22f
        val bodyTop    = h * 0.42f
        val bodyWidth  = w * 0.56f
        val bodyHeight = h * 0.46f
        val cornerR    = w * 0.10f

        // ── Shackle (U-shape above body) ──────────────────────────────────
        val shackleCx    = w * 0.50f
        val shackleCy    = h * 0.35f
        val shackleRadX  = w * 0.19f
        val shackleRadY  = h * 0.22f
        drawArc(
            color      = LockAmberDark,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter  = false,
            topLeft    = Offset(shackleCx - shackleRadX, shackleCy - shackleRadY),
            size       = Size(shackleRadX * 2, shackleRadY * 2),
            style      = Stroke(width = w * 0.10f, cap = StrokeCap.Round)
        )

        // ── Lock body (rounded rect) ───────────────────────────────────────
        drawRoundRect(
            color       = LockAmber,
            topLeft     = Offset(bodyLeft, bodyTop),
            size        = Size(bodyWidth, bodyHeight),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerR)
        )

        // ── Keyhole circle ────────────────────────────────────────────────
        val khCx = w * 0.50f
        val khCy = bodyTop + bodyHeight * 0.38f
        drawCircle(
            color  = LockShade,
            radius = w * 0.085f,
            center = Offset(khCx, khCy)
        )

        // ── Keyhole slot (rectangle below circle) ─────────────────────────
        val slotW = w * 0.08f
        val slotH = bodyHeight * 0.24f
        drawRect(
            color   = LockShade,
            topLeft = Offset(khCx - slotW / 2, khCy),
            size    = Size(slotW, slotH)
        )
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun SpotifyNotLoggedInScreenPreview() {
    KidstuneTheme {
        SpotifyNotLoggedInScreen()
    }
}
