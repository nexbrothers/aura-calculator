package app.aurio.ui.levelup

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import app.aurio.R
import app.aurio.domain.AuraLevel
import app.aurio.ui.theme.AuraPalettes
import app.aurio.ui.theme.AurioColors
import java.io.File
import kotlin.random.Random

/**
 * A story-sized image of the user's aura, level and score: nothing about which apps they use or
 * for how long. Made on the phone and handed to the share sheet only when the user taps Share.
 */
object ShareCard {
    private const val W = 1080
    private const val H = 1920

    fun render(context: Context, level: AuraLevel, score: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val p = AuraPalettes.of(level)
        canvas.drawColor(p.base.toArgb())
        blob(canvas, p.c1, 0.10f, 0.10f, 0.70f)
        blob(canvas, p.c2, 0.92f, 0.18f, 0.62f)
        blob(canvas, p.c3, 0.28f, 0.78f, 0.72f)
        blob(canvas, p.c4, 0.90f, 0.86f, 0.56f)
        grain(canvas)

        val bricolage = ResourcesCompat.getFont(context, R.font.bricolage_grotesque) ?: Typeface.DEFAULT_BOLD
        val onest = ResourcesCompat.getFont(context, R.font.onest) ?: Typeface.DEFAULT
        val ink = AurioColors.Ink.toArgb()
        val left = 96f

        // Everything sits inside the area story apps leave clear: roughly the top 250 px and the
        // bottom 340 px are covered by their progress bar and reply box.
        text(canvas, "aurio", left, 340f, 64f, bricolage, 800, ink)
        text(canvas, "I reached", left, 940f, 56f, onest, 500, ink)
        text(canvas, level.label, left, 1110f, 190f, bricolage, 800, ink, tracking = -0.04f)
        text(canvas, "$score", left - 6f, 1400f, 300f, bricolage, 800, ink, tracking = -0.02f)
        text(canvas, "aura", left, 1490f, 56f, onest, 500, ink)
        text(canvas, "Scored privately on my phone by Aurio.", left, 1560f, 40f, onest, 500, ink)
        return bitmap
    }

    /** Removes any card left in the cache (Delete all data). */
    fun clear(context: Context) {
        File(context.cacheDir, "share").deleteRecursively()
    }

    /** Writes the card to the app's private cache and opens the share sheet for it. */
    fun share(context: Context, bitmap: Bitmap) {
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        val file = File(dir, "aurio-level.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.share", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType("image/png")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, "Share your aura").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun blob(canvas: Canvas, color: Color, x: Float, y: Float, radiusFraction: Float) {
        val cx = x * W
        val cy = y * H
        val r = radiusFraction * H * 0.75f
        val c = color.toArgb()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                cx, cy, r,
                intArrayOf(c, color.copy(alpha = 0.55f).toArgb(), color.copy(alpha = 0f).toArgb()),
                floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP,
            )
        }
        canvas.drawCircle(cx, cy, r, paint)
    }

    private fun grain(canvas: Canvas) {
        val r = Random(7)
        val light = Paint().apply { color = Color.White.copy(alpha = 0.18f).toArgb() }
        val dark = Paint().apply { color = Color.Black.copy(alpha = 0.05f).toArgb() }
        repeat(9000) { i ->
            val x = r.nextFloat() * W
            val y = r.nextFloat() * H
            canvas.drawRect(x, y, x + 3f, y + 3f, if (i % 2 == 0) light else dark)
        }
    }

    private fun text(
        canvas: Canvas, s: String, x: Float, y: Float, size: Float, face: Typeface, weight: Int, color: Int,
        tracking: Float = 0f,
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = face
            textSize = size
            this.color = color
            letterSpacing = tracking
            // The bundled fonts are variable; pick the weight the app uses.
            fontVariationSettings = "'wght' $weight"
        }
        canvas.drawText(s, x, y, paint)
    }
}
