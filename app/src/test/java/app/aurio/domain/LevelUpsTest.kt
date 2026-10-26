package app.aurio.domain

import app.aurio.domain.AuraLevel.FLICKER
import app.aurio.domain.AuraLevel.GLOW
import app.aurio.domain.AuraLevel.HALO
import app.aurio.domain.AuraLevel.RADIANT
import app.aurio.domain.AuraLevel.STATIC
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LevelUpsTest {
    private var nextId = 1L
    private fun change(from: AuraLevel, to: AuraLevel) = LevelChange(nextId++, from, to)

    @Test fun oneRiseIsCelebrated() {
        assertEquals(FLICKER, LevelUps.toCelebrate(listOf(change(STATIC, FLICKER)), FLICKER))
    }

    @Test fun nothingUnseenShowsNothing() {
        assertNull(LevelUps.toCelebrate(emptyList(), HALO))
    }

    @Test fun aDropIsNeverCelebrated() {
        assertNull(LevelUps.toCelebrate(listOf(change(GLOW, FLICKER)), FLICKER))
    }

    @Test fun severalDaysAtOnceShowTheHighestStillHeld() {
        val unseen = listOf(change(FLICKER, GLOW), change(GLOW, RADIANT))
        assertEquals(RADIANT, LevelUps.toCelebrate(unseen, RADIANT))
    }

    @Test fun aLevelReachedThenLostIsNotCelebrated() {
        // Reached Radiant, then dropped back to Glow before the app was opened.
        val unseen = listOf(change(GLOW, RADIANT), change(RADIANT, GLOW))
        assertNull(LevelUps.toCelebrate(unseen, GLOW))
    }

    @Test fun anEarlierRiseStillHeldIsCelebratedAfterALaterLoss() {
        // Flicker to Glow, Glow to Radiant, back to Glow: Glow is still held.
        val unseen = listOf(change(FLICKER, GLOW), change(GLOW, RADIANT), change(RADIANT, GLOW))
        assertEquals(GLOW, LevelUps.toCelebrate(unseen, GLOW))
    }

    @Test fun aJumpOverALevelCelebratesWhereItLanded() {
        assertEquals(HALO, LevelUps.toCelebrate(listOf(change(RADIANT, HALO)), HALO))
    }
}
