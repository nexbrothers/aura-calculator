package app.aurio.ui.levelup

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import app.aurio.R
import app.aurio.domain.AuraLevel
import app.aurio.ui.aura.AuraPainter
import app.aurio.ui.theme.AuraPalettes
import app.aurio.ui.theme.AurioColors
import java.io.File

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
        AuraPainter.draw(canvas, AuraPalettes.of(level), W, H)

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
