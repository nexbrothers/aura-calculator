package app.aurio.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AuraScoringTest {

    private fun limited(used: Int?, limit: Int, label: String = "App") =
        AppUsage("pkg.$label", label, AppCategory.LIMITED, used, limit)

    @Test fun levelsMatchRanges() {
        assertEquals(AuraLevel.STATIC, AuraLevel.of(0))
        assertEquals(AuraLevel.FLICKER, AuraLevel.of(200))
        assertEquals(AuraLevel.GLOW, AuraLevel.of(599))
        assertEquals(AuraLevel.RADIANT, AuraLevel.of(705))
        assertEquals(AuraLevel.HALO, AuraLevel.of(1000))
        assertEquals(AuraLevel.HALO, AuraLevel.of(1200)) // clamped
    }

    @Test fun focusIsCappedAt100PerDay() {
        assertEquals(listOf(50, 50, 0), AuraScoring.focusEarnings(listOf(50, 50, 25)))
        assertEquals(listOf(90, 10), AuraScoring.focusEarnings(listOf(90, 50)))
        assertEquals(25, AuraScoring.potentialFocus(listOf(25, 25, 25), 50))
    }

    @Test fun underLimitProjectsBonus() {
        assertEquals(LimitStatus.Under(7, 20), AuraScoring.limitStatus(limited(23, 30)))
        assertEquals(LimitStatus.Under(0, 20), AuraScoring.limitStatus(limited(30, 30)))
    }

    @Test fun overLimitPenaltyIsCappedAt60() {
        assertEquals(LimitStatus.Over(17, 17), AuraScoring.limitStatus(limited(47, 30)))
        assertEquals(LimitStatus.Over(90, 60), AuraScoring.limitStatus(limited(120, 30)))
    }

    @Test fun unknownAndNeutralNeverScore() {
        assertEquals(LimitStatus.Unknown, AuraScoring.limitStatus(limited(null, 30)))
        val maps = AppUsage("maps", "Maps", AppCategory.NEUTRAL, 300, 0)
        assertEquals(LimitStatus.NotScored, AuraScoring.limitStatus(maps))
        val day = AuraScoring.today(emptyList(), listOf(limited(null, 30), maps))
        assertEquals(0, day.total)
        assertTrue(day.lines.any { it.kind == LineKind.UNKNOWN })
    }

    @Test fun sampleDayMatchesDesign() {
        val sessions = listOf(FocusSession(25, "11:30 am"), FocusSession(25, "6:10 pm"))
        val apps = listOf(limited(23, 30, "Instagram"), limited(47, 30, "YouTube"), limited(12, 20, "Snapchat"), limited(4, 15, "X"))
        val day = AuraScoring.today(sessions, apps)
        assertEquals(50, day.focusAura)
        assertEquals(93, day.total) // +50 focus, +60 projected, -17 YouTube
    }
}
