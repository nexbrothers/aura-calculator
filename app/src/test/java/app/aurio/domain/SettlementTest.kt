package app.aurio.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettlementTest {
    private fun limited(pkg: String, used: Int?, limit: Int) =
        AppUsage(pkg, pkg, AppCategory.LIMITED, used, limit)

    @Test fun designDayIsPlus93() {
        val apps = listOf(limited("ig", 23, 30), limited("yt", 47, 30), limited("snap", 12, 20), limited("x", 4, 15))
        val day = Settlement.settle(apps, listOf(25, 25), previousTotal = 612, previousStreak = 6, isFirstDay = false)
        assertEquals(93, day.delta)
        assertEquals(705, day.totalAfter)
        assertEquals(7, day.streakAfter)
        assertFalse(day.partial)
        assertEquals(AuraLevel.RADIANT, day.levelTo)
    }

    @Test fun unknownUsageNeverScoresAndMarksPartial() {
        val day = Settlement.settle(listOf(limited("ig", null, 30)), emptyList(), 100, 3, false)
        assertEquals(0, day.delta)
        assertTrue(day.partial)
        assertEquals(0, day.streakAfter)
    }

    @Test fun crossingALevelLineIsReported() {
        val day = Settlement.settle(listOf(limited("ig", 0, 30)), listOf(25), 790, 0, false)
        assertEquals(AuraLevel.RADIANT, day.levelFrom)
        assertEquals(AuraLevel.HALO, day.levelTo)
        assertEquals(835, day.totalAfter)
    }

    @Test fun scoreNeverGoesBelowZeroAndDeltaIsTheRealChange() {
        val day = Settlement.settle(listOf(limited("a", 200, 10), limited("b", 200, 10)), emptyList(), 30, 4, false)
        assertEquals(0, day.totalAfter)
        assertEquals(-30, day.delta)
        assertEquals(0, day.streakAfter)
    }

    @Test fun scoreNeverExceedsOneThousand() {
        val day = Settlement.settle(listOf(limited("a", 0, 30)), listOf(25, 25, 25, 25, 25), 990, 1, false)
        assertEquals(1000, day.totalAfter)
        assertEquals(10, day.delta)
    }

    @Test fun neutralAndEssentialAppsAreIgnored() {
        val apps = listOf(AppUsage("m", "Maps", AppCategory.NEUTRAL, 500, 0), AppUsage("w", "WhatsApp", AppCategory.ESSENTIAL, 500, 0))
        val day = Settlement.settle(apps, emptyList(), 50, 0, false)
        assertEquals(0, day.delta)
        assertTrue(day.entries.isEmpty())
    }

    @Test fun firstDayIsPartial() {
        assertTrue(Settlement.settle(emptyList(), emptyList(), 0, 0, true).partial)
    }
}
