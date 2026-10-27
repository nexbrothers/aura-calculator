package app.aurio.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.aurio.domain.AuraLevel

/** Core colour tokens. Colour belongs to the aura; the rest of the UI stays quiet. */
object AurioColors {
    val Ink = Color(0xFF17161C)
    val InkMuted = Color(0xFF55545C)
    val InkSubtle = Color(0xFF6B6A73) // passes 4.5:1 on Paper and Surface
    val Paper = Color(0xFFF6F6F3)
    val Surface = Color(0xFFFFFFFF)
    val SurfaceSunken = Color(0xFFEDEBE4)
    val Divider = Color(0xFFF1F0EC)
    val Track = Color(0xFFEEEDF1)

    val Gain = Color(0xFF0E9467)
    val GainText = Color(0xFF0B7A55) // darker than the brief's #0E9467 so small text passes AA
    val GainBg = Color(0xFFE2F6EC)
    val Loss = Color(0xFFE0402F)
    val LossText = Color(0xFFC23423)
    val LossBg = Color(0xFFFDE8E5)
    val Unknown = Color(0xFF5E5D66) // brief's #8A8894 fails AA for small text
    val UnknownBg = Color(0xFFEEEDF1)
    val Neutral = Color(0xFF5E5D66)
    val NeutralBg = Color(0xFFF1F0EC)

    /** Streak flame icon only (never text): warm, so a streak reads as warmth, not as a gain. */
    val Streak = Color(0xFFE0702F)
    /** Soft card shadow: barely there, depth without borders. */
    val Shadow = Color(0xFF17161C)
}

/** Subtle depth only. */
object AurioElevation {
    val card = 10.dp
    val hero = 18.dp
}

/** One aura colour field per level: a base wash plus four drifting blobs. */
data class AuraPalette(val base: Color, val c1: Color, val c2: Color, val c3: Color, val c4: Color)

object AuraPalettes {
    val Static = AuraPalette(Color(0xFFE9EAEE), Color(0xFFC9CCD3), Color(0xFFB7BBC6), Color(0xFFDADDE3), Color(0xFFA9AEBA))
    val Flicker = AuraPalette(Color(0xFFFFEDE4), Color(0xFFFFA27A), Color(0xFFFF7F6B), Color(0xFFFFC59A), Color(0xFFFF9C8A))
    val Glow = AuraPalette(Color(0xFFFBF8E6), Color(0xFFFFE27A), Color(0xFF9FF0CF), Color(0xFFFFF0A8), Color(0xFFC6F5B8))
    val Radiant = AuraPalette(Color(0xFFFFF1E6), Color(0xFFFF9EC7), Color(0xFFFFE27A), Color(0xFF9FF0CF), Color(0xFFFFB27A))
    val Halo = AuraPalette(Color(0xFFEEF6FB), Color(0xFF9FF0CF), Color(0xFFA7D8FF), Color(0xFFC9B8FF), Color(0xFFBFF3FF))

    fun of(level: AuraLevel): AuraPalette = when (level) {
        AuraLevel.STATIC -> Static
        AuraLevel.FLICKER -> Flicker
        AuraLevel.GLOW -> Glow
        AuraLevel.RADIANT -> Radiant
        AuraLevel.HALO -> Halo
    }
}

/** 4 dp grid. */
object AurioSpacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp // screen margin
    val xxl = 24.dp
    val xxxl = 32.dp
    val touchMin = 48.dp
    val buttonHeight = 54.dp
}

object AurioRadius {
    val hero = 28.dp
    val card = 20.dp
    val tile = 12.dp
}

val AurioShapes = Shapes(
    small = RoundedCornerShape(AurioRadius.tile),
    medium = RoundedCornerShape(AurioRadius.card),
    large = RoundedCornerShape(AurioRadius.hero),
)
