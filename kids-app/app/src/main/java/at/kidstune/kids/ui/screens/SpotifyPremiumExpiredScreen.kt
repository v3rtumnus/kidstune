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
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import at.kidstune.kids.ui.theme.KidstuneTheme
import at.kidstune.kids.ui.theme.MusicPrimary

private val NoteBody  = MusicPrimary                // purple
private val NoteStem  = Color(0xFF4A2F8C)           // darker purple
private val SlashRed  = Color(0xFFE53935)

/**
 * Shown when [at.kidstune.kids.playback.SpotifyConnectionError.PREMIUM_REQUIRED] is detected.
 *
 * The parent needs to renew the Spotify Premium subscription.
 */
@Composable
fun SpotifyPremiumExpiredScreen(modifier: Modifier = Modifier) {
    KidsErrorScreen(
        modifier              = modifier,
        contentDescriptionTag = "Spotify Premium abgelaufen",
        illustration          = { MusicNoteWithSlashIllustration() },
        title                 = "Musik geht gerade nicht.",
        subtitle              = "Bitte Mama oder Papa fragen."
    )
}

@Composable
private fun MusicNoteWithSlashIllustration(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(160.dp)) {
        val w = size.width
        val h = size.height

        // ── Note head (filled oval, slightly tilted) ───────────────────────
        val noteHeadCx = w * 0.40f
        val noteHeadCy = h * 0.66f
        val noteHeadW  = w * 0.26f
        val noteHeadH  = h * 0.18f
        drawOval(
            color   = NoteBody,
            topLeft = Offset(noteHeadCx - noteHeadW / 2, noteHeadCy - noteHeadH / 2),
            size    = Size(noteHeadW, noteHeadH)
        )

        // ── Stem (vertical line from note head going up) ───────────────────
        val stemX      = noteHeadCx + noteHeadW * 0.38f
        val stemBottom = noteHeadCy - noteHeadH * 0.10f
        val stemTop    = h * 0.12f
        drawLine(
            color       = NoteStem,
            start       = Offset(stemX, stemBottom),
            end         = Offset(stemX, stemTop),
            strokeWidth = w * 0.052f,
            cap         = StrokeCap.Round
        )

        // ── Flag 1 (short diagonal from stem top) ─────────────────────────
        val flag1Path = Path().apply {
            moveTo(stemX,          stemTop)
            cubicTo(
                stemX + w * 0.22f, stemTop + h * 0.06f,
                stemX + w * 0.20f, stemTop + h * 0.12f,
                stemX,             stemTop + h * 0.15f
            )
        }
        drawPath(path = flag1Path, color = NoteStem, style = Stroke(width = w * 0.052f, cap = StrokeCap.Round))

        // ── Flag 2 (slightly lower) ────────────────────────────────────────
        val flag2Offset = h * 0.10f
        val flag2Path = Path().apply {
            moveTo(stemX,          stemTop + flag2Offset)
            cubicTo(
                stemX + w * 0.20f, stemTop + flag2Offset + h * 0.06f,
                stemX + w * 0.18f, stemTop + flag2Offset + h * 0.12f,
                stemX,             stemTop + flag2Offset + h * 0.15f
            )
        }
        drawPath(path = flag2Path, color = NoteStem, style = Stroke(width = w * 0.052f, cap = StrokeCap.Round))

        // ── Red "no" slash (circle + diagonal line) ────────────────────────
        val slashR = w * 0.32f
        val slashCx = w * 0.58f
        val slashCy = h * 0.38f
        drawCircle(
            color  = SlashRed,
            radius = slashR,
            center = Offset(slashCx, slashCy),
            style  = Stroke(width = w * 0.065f)
        )
        val angle = Math.toRadians(45.0).toFloat()
        val slashLen = slashR * 0.90f
        drawLine(
            color       = SlashRed,
            start       = Offset(slashCx - slashLen * kotlin.math.cos(angle), slashCy + slashLen * kotlin.math.sin(angle)),
            end         = Offset(slashCx + slashLen * kotlin.math.cos(angle), slashCy - slashLen * kotlin.math.sin(angle)),
            strokeWidth = w * 0.065f,
            cap         = StrokeCap.Round
        )
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun SpotifyPremiumExpiredScreenPreview() {
    KidstuneTheme {
        SpotifyPremiumExpiredScreen()
    }
}
