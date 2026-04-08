package at.kidstune.kids.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate

private data class ConfettiParticle(
    val startX: Float,       // 0..1 – fraction of canvas width
    val startY: Float,       // −0.6..0 – starts above the canvas
    val vx: Float,           // horizontal drift per unit progress
    val vy: Float,           // vertical fall speed per unit progress
    val gravity: Float,      // extra downward acceleration
    val color: Color,
    val width: Float,        // rectangle width in px
    val height: Float,       // rectangle height in px
    val rotation: Float,     // initial rotation degrees
    val rotationSpeed: Float // degrees per unit progress
)

private val CONFETTI_COLORS = listOf(
    Color(0xFFFF6B6B), // red
    Color(0xFFFFD93D), // yellow
    Color(0xFF6BCB77), // green
    Color(0xFF4D96FF), // blue
    Color(0xFFFF922B), // orange
    Color(0xFFCC5DE8), // purple
    Color(0xFFFF63B0), // pink
    Color(0xFF00D2FF), // cyan
)

/** Produces a deterministic set of particles (fixed seed → stable across recompositions). */
private fun generateParticles(count: Int = 60): List<ConfettiParticle> {
    val rng = java.util.Random(42L)
    return List(count) {
        ConfettiParticle(
            startX       = rng.nextFloat(),
            startY       = -0.05f - rng.nextFloat() * 0.55f,
            vx           = (rng.nextFloat() - 0.5f) * 0.35f,
            vy           = 0.25f + rng.nextFloat() * 0.45f,
            gravity      = 0.12f + rng.nextFloat() * 0.10f,
            color        = CONFETTI_COLORS[rng.nextInt(CONFETTI_COLORS.size)],
            width        = 14f + rng.nextFloat() * 20f,
            height       = 7f  + rng.nextFloat() * 10f,
            rotation     = rng.nextFloat() * 360f,
            rotationSpeed = (rng.nextFloat() - 0.5f) * 720f
        )
    }
}

/**
 * Full-canvas confetti particle animation that runs for [durationMs] milliseconds.
 * Particles fall from above, drift sideways, and fade out in the final 30 % of the animation.
 * Uses a fixed random seed so the particle layout is identical across recompositions.
 */
@Composable
fun ConfettiCanvas(
    modifier: Modifier = Modifier,
    durationMs: Int = 3000
) {
    val particles = remember { generateParticles() }
    val progress  = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        progress.animateTo(
            targetValue    = 1f,
            animationSpec  = tween(durationMillis = durationMs, easing = LinearEasing)
        )
    }

    Canvas(modifier = modifier) {
        val p = progress.value
        particles.forEach { particle ->
            val x = (particle.startX + particle.vx * p) * size.width
            val y = (particle.startY + particle.vy * p + particle.gravity * p * p) * size.height

            // Skip particles still above the canvas
            if (y < -particle.height) return@forEach

            // Fade out during the last 30 % of the animation
            val alpha = when {
                p < 0.70f -> 1f
                else      -> 1f - ((p - 0.70f) / 0.30f)
            }.coerceIn(0f, 1f)

            if (alpha <= 0f) return@forEach

            val angle = particle.rotation + particle.rotationSpeed * p
            rotate(degrees = angle, pivot = Offset(x, y)) {
                drawRect(
                    color   = particle.color.copy(alpha = alpha),
                    topLeft = Offset(x - particle.width / 2f, y - particle.height / 2f),
                    size    = Size(particle.width, particle.height)
                )
            }
        }
    }
}
