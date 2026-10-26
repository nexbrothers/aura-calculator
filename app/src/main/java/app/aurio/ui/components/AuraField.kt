package app.aurio.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import app.aurio.ui.theme.AuraPalette
import kotlin.random.Random

/**
 * The signature element: a soft, grainy colour field with slowly drifting blobs.
 * Colours cross-fade when the level changes. [brightness] > 1 brightens briefly on a gain.
 */
@Composable
fun AuraField(
    palette: AuraPalette,
    modifier: Modifier = Modifier,
    brightness: Float = 1f,
    animate: Boolean = true,
) {
    val spec = tween<Color>(1200)
    val base by animateColorAsState(palette.base, spec, label = "base")
    val c1 by animateColorAsState(palette.c1, spec, label = "c1")
    val c2 by animateColorAsState(palette.c2, spec, label = "c2")
    val c3 by animateColorAsState(palette.c3, spec, label = "c3")
    val c4 by animateColorAsState(palette.c4, spec, label = "c4")

    val drift = rememberInfiniteTransition(label = "drift")
    val a by drift.animateFloat(0f, 1f, infiniteRepeatable(tween(15000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "a")
    val b by drift.animateFloat(1f, 0f, infiniteRepeatable(tween(20000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "b")
    val pa = if (animate) a else 0.5f
    val pb = if (animate) b else 0.5f

    val grain = remember { val r = Random(7); List(1400) { Offset(r.nextFloat(), r.nextFloat()) } }

    Canvas(modifier) {
        drawRect(base)
        blob(c1, 0.05f + 0.10f * pa, 0.12f - 0.06f * pa, 0.62f)
        blob(c2, 0.95f - 0.08f * pb, 0.10f + 0.05f * pb, 0.58f)
        blob(c3, 0.30f + 0.08f * pb, 0.80f - 0.05f * pb, 0.66f)
        blob(c4, 0.92f - 0.06f * pa, 0.78f + 0.04f * pa, 0.50f)
        if (brightness > 1f) drawRect(Color.White.copy(alpha = ((brightness - 1f) * 1.5f).coerceAtMost(0.4f)))
        val dot = Size(1.6f, 1.6f)
        grain.forEachIndexed { i, o ->
            drawRect(
                if (i % 2 == 0) Color.White.copy(alpha = 0.18f) else Color.Black.copy(alpha = 0.05f),
                topLeft = Offset(o.x * size.width, o.y * size.height), size = dot,
            )
        }
    }
}

private fun DrawScope.blob(color: Color, x: Float, y: Float, radiusFraction: Float) {
    val center = Offset(x * size.width, y * size.height)
    val radius = radiusFraction * size.maxDimension * 0.75f
    drawCircle(
        brush = Brush.radialGradient(
            0f to color, 0.55f to color.copy(alpha = 0.55f), 1f to color.copy(alpha = 0f),
            center = center, radius = radius,
        ),
        radius = radius, center = center,
    )
}
