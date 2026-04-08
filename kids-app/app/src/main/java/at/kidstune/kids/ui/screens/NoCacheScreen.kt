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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import at.kidstune.kids.ui.theme.KidstuneTheme

private val PlaneSky    = Color(0xFF42A5F5)  // sky blue
private val PlaneLight  = Color(0xFF90CAF9)  // lighter accent
private val PlaneWindow = Color(0xFFE3F2FD)  // near-white

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
    KidsErrorScreen(
        modifier              = modifier,
        contentDescriptionTag = "Kein Cache verfügbar",
        illustration          = { AirplaneIllustration() },
        title                 = "Bitte mit WLAN verbinden",
        subtitle              = "Für den ersten Start wird eine Internetverbindung benötigt."
    )
}

@Composable
private fun AirplaneIllustration(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(180.dp)) {
        val w = size.width
        val h = size.height

        // ── Fuselage (horizontal wide ellipse, centre of canvas) ───────────
        val fusCx    = w * 0.48f
        val fusCy    = h * 0.50f
        val fusW     = w * 0.72f
        val fusH     = h * 0.22f
        drawOval(
            color   = PlaneSky,
            topLeft = Offset(fusCx - fusW / 2, fusCy - fusH / 2),
            size    = Size(fusW, fusH)
        )

        // ── Nose cone (triangle on right) ─────────────────────────────────
        val nosePath = Path().apply {
            moveTo(fusCx + fusW / 2 - w * 0.02f, fusCy)          // centre attach
            lineTo(fusCx + fusW / 2 + w * 0.14f, fusCy)          // tip
            lineTo(fusCx + fusW / 2 - w * 0.02f, fusCy - fusH * 0.28f) // upper
            lineTo(fusCx + fusW / 2 - w * 0.02f, fusCy + fusH * 0.28f) // lower
            close()
        }
        drawPath(path = nosePath, color = PlaneSky)

        // ── Main wings (large diamond shape) ──────────────────────────────
        val wingPath = Path().apply {
            moveTo(fusCx - w * 0.02f, fusCy)              // centre left attach
            lineTo(fusCx + w * 0.14f, fusCy)              // centre right attach
            lineTo(fusCx + w * 0.06f, fusCy - h * 0.28f) // upper wing tip
            lineTo(fusCx - w * 0.04f, fusCy - h * 0.26f)
            close()
        }
        val wingPathLower = Path().apply {
            moveTo(fusCx - w * 0.02f, fusCy)
            lineTo(fusCx + w * 0.14f, fusCy)
            lineTo(fusCx + w * 0.06f, fusCy + h * 0.28f)
            lineTo(fusCx - w * 0.04f, fusCy + h * 0.26f)
            close()
        }
        drawPath(path = wingPath,      color = PlaneSky)
        drawPath(path = wingPathLower, color = PlaneSky)

        // ── Wing accent (lighter stripe) ───────────────────────────────────
        drawLine(
            color       = PlaneLight,
            start       = Offset(fusCx + w * 0.08f, fusCy - h * 0.22f),
            end         = Offset(fusCx + w * 0.10f, fusCy),
            strokeWidth = w * 0.035f,
            cap         = StrokeCap.Round
        )
        drawLine(
            color       = PlaneLight,
            start       = Offset(fusCx + w * 0.08f, fusCy + h * 0.22f),
            end         = Offset(fusCx + w * 0.10f, fusCy),
            strokeWidth = w * 0.035f,
            cap         = StrokeCap.Round
        )

        // ── Tail fin (small triangle on left side, pointing up) ────────────
        val tailPath = Path().apply {
            moveTo(fusCx - fusW / 2 + w * 0.04f, fusCy - fusH * 0.45f) // attach upper
            lineTo(fusCx - fusW / 2 + w * 0.04f, fusCy)                  // attach lower
            lineTo(fusCx - fusW / 2 + w * 0.20f, fusCy - fusH * 0.45f)  // inner
            close()
        }
        drawPath(path = tailPath, color = PlaneLight)

        // ── Windows (two small circles on fuselage) ────────────────────────
        val windowY = fusCy - fusH * 0.12f
        listOf(fusCx + w * 0.06f, fusCx + w * 0.16f).forEach { wx ->
            drawCircle(
                color  = PlaneWindow,
                radius = w * 0.040f,
                center = Offset(wx, windowY)
            )
        }

        // ── Trail dashes (three short lines to the left of tail) ──────────
        listOf(0f, h * 0.06f, h * 0.12f).forEachIndexed { idx, dy ->
            drawLine(
                color       = PlaneLight,
                start       = Offset(fusCx - fusW / 2 - w * 0.06f - idx * w * 0.04f, fusCy + dy),
                end         = Offset(fusCx - fusW / 2 - w * 0.16f - idx * w * 0.04f, fusCy + dy),
                strokeWidth = h * 0.022f,
                cap         = StrokeCap.Round
            )
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun NoCacheScreenPreview() {
    KidstuneTheme {
        NoCacheScreen()
    }
}
