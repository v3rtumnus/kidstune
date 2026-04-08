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
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import at.kidstune.kids.ui.theme.KidstuneTheme

private val BoxOrange   = Color(0xFFFF7043)
private val BoxDark     = Color(0xFFBF360C)
private val BlockBlue   = Color(0xFF42A5F5)
private val BlockGreen  = Color(0xFF66BB6A)
private val BlockPurple = Color(0xFFAB47BC)

/**
 * Shown when a Room write fails due to insufficient storage (disk-full error).
 *
 * The parent needs to free up storage space on the device.
 */
@Composable
fun StorageFullScreen(modifier: Modifier = Modifier) {
    KidsErrorScreen(
        modifier              = modifier,
        contentDescriptionTag = "Speicher voll",
        illustration          = { OverflowingBoxIllustration() },
        title                 = "Nicht genug Platz!",
        subtitle              = "Bitte Mama oder Papa fragen."
    )
}

@Composable
private fun OverflowingBoxIllustration(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(160.dp)) {
        val w = size.width
        val h = size.height

        val boxLeft   = w * 0.15f
        val boxTop    = h * 0.48f
        val boxWidth  = w * 0.70f
        val boxHeight = h * 0.42f

        // ── Box body (filled) ──────────────────────────────────────────────
        drawRect(
            color   = BoxOrange,
            topLeft = Offset(boxLeft, boxTop),
            size    = Size(boxWidth, boxHeight)
        )

        // ── Box outline ────────────────────────────────────────────────────
        drawRect(
            color   = BoxDark,
            topLeft = Offset(boxLeft, boxTop),
            size    = Size(boxWidth, boxHeight),
            style   = Stroke(width = w * 0.045f, join = StrokeJoin.Round)
        )

        // ── Box flaps (open top) ───────────────────────────────────────────
        val flapW = boxWidth * 0.48f
        val flapH = h * 0.10f
        // left flap (tilted outward)
        val leftFlapPath = Path().apply {
            moveTo(boxLeft,             boxTop)
            lineTo(boxLeft + flapW,     boxTop)
            lineTo(boxLeft + flapW * 0.85f, boxTop - flapH)
            lineTo(boxLeft - w * 0.02f, boxTop - flapH * 0.80f)
            close()
        }
        drawPath(path = leftFlapPath, color = BoxOrange)
        drawPath(path = leftFlapPath, color = BoxDark, style = Stroke(width = w * 0.040f, join = StrokeJoin.Round))

        // right flap (tilted outward)
        val rightFlapPath = Path().apply {
            moveTo(boxLeft + boxWidth,              boxTop)
            lineTo(boxLeft + boxWidth - flapW,      boxTop)
            lineTo(boxLeft + boxWidth - flapW * 0.85f, boxTop - flapH)
            lineTo(boxLeft + boxWidth + w * 0.02f,  boxTop - flapH * 0.80f)
            close()
        }
        drawPath(path = rightFlapPath, color = BoxOrange)
        drawPath(path = rightFlapPath, color = BoxDark, style = Stroke(width = w * 0.040f, join = StrokeJoin.Round))

        // ── Overflowing blocks (small coloured squares spilling out) ───────
        val blockSize = w * 0.15f
        val blockData = listOf(
            Triple(w * 0.26f, boxTop - blockSize * 1.05f, BlockBlue),
            Triple(w * 0.44f, boxTop - blockSize * 1.40f, BlockGreen),
            Triple(w * 0.62f, boxTop - blockSize * 1.05f, BlockPurple)
        )
        blockData.forEach { (bx, by, color) ->
            drawRoundRect(
                color       = color,
                topLeft     = Offset(bx, by),
                size        = Size(blockSize, blockSize),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.025f)
            )
        }

        // ── Exclamation mark in centre of box ────────────────────────────
        val exCx = boxLeft + boxWidth / 2
        drawLine(
            color       = Color.White,
            start       = Offset(exCx, boxTop + boxHeight * 0.22f),
            end         = Offset(exCx, boxTop + boxHeight * 0.62f),
            strokeWidth = w * 0.055f,
            cap         = StrokeCap.Round
        )
        drawCircle(
            color  = Color.White,
            radius = w * 0.035f,
            center = Offset(exCx, boxTop + boxHeight * 0.78f)
        )
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun StorageFullScreenPreview() {
    KidstuneTheme {
        StorageFullScreen()
    }
}
