package app.aurio.ui.tabs

import app.aurio.domain.AppCategory
import app.aurio.domain.AppTerms
import app.aurio.domain.AppUsage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TomorrowNoteTest {
    private val today = AppUsage("ig", "Instagram", AppCategory.LIMITED, 10, 30)

    @Test fun removedApp() =
        assertEquals("Stops being tracked tomorrow", tomorrowNote(today, null))

    @Test fun categoryMoved() =
        assertEquals("Moves to neutral tomorrow", tomorrowNote(today, AppTerms("Instagram", AppCategory.NEUTRAL, null)))

    @Test fun limitRaised() =
        assertEquals("Limit is 30 min today, 45 min from tomorrow", tomorrowNote(today, AppTerms("Instagram", AppCategory.LIMITED, 45)))

    @Test fun nothingPending() =
        assertNull(tomorrowNote(today, AppTerms("Instagram", AppCategory.LIMITED, 30)))
}
