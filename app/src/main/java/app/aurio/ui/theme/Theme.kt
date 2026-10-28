package app.aurio.ui.theme

import android.provider.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.aurio.R

@OptIn(ExperimentalTextApi::class)
private fun variable(res: Int, weight: Int) =
    Font(res, FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight)))

/** Display face: big numbers and headings. */
val Bricolage = FontFamily(
    variable(R.font.bricolage_grotesque, 600),
    variable(R.font.bricolage_grotesque, 700),
    variable(R.font.bricolage_grotesque, 800),
)

/** UI face: body, labels, buttons. */
val Onest = FontFamily(
    variable(R.font.onest, 400),
    variable(R.font.onest, 500),
    variable(R.font.onest, 600),
)

/** Type scale. Display styles use tight letter-spacing for confident numerals. */
object AurioType {
    val scoreHero = TextStyle(fontFamily = Bricolage, fontWeight = FontWeight.ExtraBold, fontSize = 128.sp, lineHeight = 116.sp, letterSpacing = (-0.06).em)
    /** The score inside Home's aura card: big, but leaves the card room to breathe. */
    val scoreCard = TextStyle(fontFamily = Bricolage, fontWeight = FontWeight.ExtraBold, fontSize = 112.sp, lineHeight = 104.sp, letterSpacing = (-0.05).em)
    val scoreLarge = TextStyle(fontFamily = Bricolage, fontWeight = FontWeight.ExtraBold, fontSize = 72.sp, lineHeight = 72.sp, letterSpacing = (-0.04).em)
    val display = TextStyle(fontFamily = Bricolage, fontWeight = FontWeight.ExtraBold, fontSize = 34.sp, lineHeight = 36.sp, letterSpacing = (-0.035).em)
    /** Screen greeting and card headings that sit between display and title. */
    val heading = TextStyle(fontFamily = Bricolage, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 28.sp, letterSpacing = (-0.03).em)
    val title = TextStyle(fontFamily = Bricolage, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 24.sp, letterSpacing = (-0.03).em)
    val body = TextStyle(fontFamily = Onest, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp)
    val bodyStrong = body.copy(fontWeight = FontWeight.Medium)
    val label = TextStyle(fontFamily = Onest, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 18.sp)
    val caption = TextStyle(fontFamily = Onest, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp)
    val button = TextStyle(fontFamily = Onest, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
}

private val AurioTypography = Typography(
    displayLarge = AurioType.scoreLarge,
    headlineLarge = AurioType.display,
    titleLarge = AurioType.title,
    bodyLarge = AurioType.body,
    bodyMedium = AurioType.body.copy(fontSize = 14.sp),
    labelLarge = AurioType.button,
    labelMedium = AurioType.label,
    labelSmall = AurioType.caption,
)

private val AurioColorScheme = lightColorScheme(
    primary = AurioColors.Ink,
    onPrimary = AurioColors.Surface,
    background = AurioColors.Paper,
    onBackground = AurioColors.Ink,
    surface = AurioColors.Surface,
    onSurface = AurioColors.Ink,
    onSurfaceVariant = AurioColors.InkSubtle,
    surfaceVariant = AurioColors.SurfaceSunken,
    error = AurioColors.Loss,
    outline = AurioColors.Track,
)

/** Light mode only, per the brief. */
@Composable
fun AurioTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AurioColorScheme,
        typography = AurioTypography,
        shapes = AurioShapes,
        content = content,
    )
}

/** True when Android's "remove animations" is on, so motion follows the system as well as the in-app switch. */
@Composable
fun systemReduceMotion(): Boolean {
    val context = LocalContext.current
    val scale = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    return scale == 0f
}
