package app.aurio.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class LimitCaptionTest {
    @Test fun countedDayShowsUsage() = assertEquals("12 of 30 min used", limitCaption(12, null, 30))

    @Test fun uncountedDayStillShowsMeasuredTime() =
        assertEquals("1 of 30 min used, not counted today", limitCaption(null, 1, 30))

    @Test fun nothingMeasuredShowsTheLimit() = assertEquals("30 min limit", limitCaption(null, null, 30))
}
