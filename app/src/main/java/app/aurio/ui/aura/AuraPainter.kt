package app.aurio.ui.aura

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import app.aurio.ui.theme.AuraPalette
import kotlin.random.Random

/**
 * The aura as a still image (android.graphics), for places Compose cannot draw into: the share
 * card and home-screen widgets. Same blobs and grain as [app.aurio.ui.components.AuraField],
 * frozen at the middle of its drift.
 */
object AuraPainter {

    fun bitmap(palette: AuraPalette, width: Int, height: Int): Bitmap =
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { draw(Canvas(it), palette, width, height) }

    fun draw(canvas: Canvas, p: AuraPalette, width: Int, height: Int) {
        canvas.drawColor(p.base.toArgb())
        val big = maxOf(width, height)
        blob(canvas, p.c1, 0.10f * width, 0.10f * height, 0.70f * big * 0.75f)
        blob(canvas, p.c2, 0.92f * width, 0.18f * height, 0.62f * big * 0.75f)
        blob(canvas, p.c3, 0.28f * width, 0.78f * height, 0.72f * big * 0.75f)
        blob(canvas, p.c4, 0.90f * width, 0.86f * height, 0.56f * big * 0.75f)
        grain(canvas, width, height)
    }

    private fun blob(canvas: Canvas, color: Color, cx: Float, cy: Float, r: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                cx, cy, r,
                intArrayOf(color.toArgb(), color.copy(alpha = 0.55f).toArgb(), color.copy(alpha = 0f).toArgb()),
                floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP,
            )
        }
        canvas.drawCircle(cx, cy, r, paint)
    }

    /** Fine grain, about one speck per 230 square pixels, so it looks the same at any size. */
    private fun grain(canvas: Canvas, width: Int, height: Int) {
        val r = Random(7)
        val light = Paint().apply { color = Color.White.copy(alpha = 0.18f).toArgb() }
        val dark = Paint().apply { color = Color.Black.copy(alpha = 0.05f).toArgb() }
        val dot = maxOf(1.5f, maxOf(width, height) / 640f)
        repeat(width * height / 230) { i ->
            val x = r.nextFloat() * width
            val y = r.nextFloat() * height
            canvas.drawRect(x, y, x + dot, y + dot, if (i % 2 == 0) light else dark)
        }
    }
}
