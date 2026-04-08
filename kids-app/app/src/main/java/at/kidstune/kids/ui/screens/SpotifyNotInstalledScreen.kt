package at.kidstune.kids.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import at.kidstune.kids.ui.theme.KidstuneTheme

private val DinoGreen       = Color(0xFF4CAF50)
private val DinoGreenDark   = Color(0xFF388E3C)
private val DinoEye         = Color(0xFF1B5E20)

/**
 * Shown when [at.kidstune.kids.playback.SpotifyConnectionError.NOT_INSTALLED] is detected.
 *
 * The parent needs to install the Spotify app – no child-facing action is possible.
 */
@Composable
fun SpotifyNotInstalledScreen(modifier: Modifier = Modifier) {
    KidsErrorScreen(
        modifier             = modifier,
        contentDescriptionTag = "Spotify nicht installiert",
        illustration          = { DinosaurIllustration() },
        title                 = "Spotify fehlt!",
        subtitle              = "Gib das Handy bitte Mama oder Papa."
    )
}

@Composable
private fun DinosaurIllustration(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(180.dp)) {
        val w = size.width
        val h = size.height

        // ── Body (large oval, lower-centre) ───────────────────────────────
        val bodyLeft   = w * 0.15f
        val bodyTop    = h * 0.42f
        val bodyWidth  = w * 0.60f
        val bodyHeight = h * 0.42f
        drawOval(
            color   = DinoGreen,
            topLeft = Offset(bodyLeft, bodyTop),
            size    = Size(bodyWidth, bodyHeight)
        )

        // ── Tail (triangle pointing left from body) ────────────────────────
        val tailPath = Path().apply {
            moveTo(bodyLeft + w * 0.04f, bodyTop + bodyHeight * 0.55f)  // left on body
            lineTo(w * 0.00f,            bodyTop + bodyHeight * 0.30f)  // tail tip (left)
            lineTo(bodyLeft + w * 0.04f, bodyTop + bodyHeight * 0.20f)  // upper attach
            close()
        }
        drawPath(path = tailPath, color = DinoGreen)

        // ── Neck (rectangle connecting body to head) ───────────────────────
        drawRect(
            color   = DinoGreen,
            topLeft = Offset(bodyLeft + bodyWidth * 0.60f, h * 0.22f),
            size    = Size(w * 0.16f, h * 0.28f)
        )

        // ── Head (oval, upper-right) ───────────────────────────────────────
        val headLeft   = bodyLeft + bodyWidth * 0.48f
        val headTop    = h * 0.08f
        val headWidth  = w * 0.38f
        val headHeight = h * 0.26f
        drawOval(
            color   = DinoGreen,
            topLeft = Offset(headLeft, headTop),
            size    = Size(headWidth, headHeight)
        )

        // ── Eye (small filled circle on head) ─────────────────────────────
        drawCircle(
            color  = DinoEye,
            radius = w * 0.040f,
            center = Offset(headLeft + headWidth * 0.60f, headTop + headHeight * 0.35f)
        )
        // eye shine
        drawCircle(
            color  = Color.White,
            radius = w * 0.014f,
            center = Offset(headLeft + headWidth * 0.63f, headTop + headHeight * 0.28f)
        )

        // ── Smile (arc on lower head) ──────────────────────────────────────
        drawArc(
            color      = DinoGreenDark,
            startAngle = 10f,
            sweepAngle = 45f,
            useCenter  = false,
            topLeft    = Offset(headLeft + headWidth * 0.30f, headTop + headHeight * 0.50f),
            size       = Size(headWidth * 0.45f, headHeight * 0.40f),
            style      = Stroke(width = w * 0.025f)
        )

        // ── Spikes (3 triangles on top of head) ───────────────────────────
        val spikeBaseY = headTop + headHeight * 0.08f
        val spikeW     = w * 0.07f
        val spikeH     = h * 0.09f
        listOf(0.35f, 0.50f, 0.65f).forEach { xFrac ->
            val sx = headLeft + headWidth * xFrac
            val spikePath = Path().apply {
                moveTo(sx,              spikeBaseY)
                lineTo(sx + spikeW,     spikeBaseY)
                lineTo(sx + spikeW / 2, spikeBaseY - spikeH)
                close()
            }
            drawPath(path = spikePath, color = DinoGreenDark)
        }

        // ── Legs (two rectangles below body) ──────────────────────────────
        val legW  = w * 0.10f
        val legH  = h * 0.14f
        val legY  = bodyTop + bodyHeight - legH * 0.15f
        listOf(bodyLeft + bodyWidth * 0.28f, bodyLeft + bodyWidth * 0.56f).forEach { lx ->
            drawRect(
                color   = DinoGreenDark,
                topLeft = Offset(lx, legY),
                size    = Size(legW, legH)
            )
            // foot
            drawRect(
                color   = DinoGreenDark,
                topLeft = Offset(lx - legW * 0.20f, legY + legH - legH * 0.25f),
                size    = Size(legW * 1.40f, legH * 0.25f)
            )
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun SpotifyNotInstalledScreenPreview() {
    KidstuneTheme {
        SpotifyNotInstalledScreen()
    }
}
