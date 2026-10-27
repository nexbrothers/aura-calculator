package app.aurio.ui.components

import app.aurio.data.NotCountedReason
import app.aurio.ui.home.streakLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class NotCountedTest {
    @Test fun accessOffSaysNotTracked() {
        assertEquals("Not tracked", NotCounted.pill(NotCountedReason.ACCESS_OFF))
    }

    @Test fun offEarlierSaysWhenItCountsAgain() {
        assertEquals("Counts tomorrow", NotCounted.pill(NotCountedReason.OFF_EARLIER_TODAY))
    }

    @Test fun neverSaysUnknown() {
        (NotCountedReason.entries + listOf(null)).forEach { assertFalse(NotCounted.pill(it).contains("Unknown")) }
    }

    @Test fun streakLabels() {
        assertEquals("No streak yet", streakLabel(0))
        assertEquals("1-day streak", streakLabel(1))
        assertEquals("6-day streak", streakLabel(6))
    }
}
