package app.aurio.ui.home

import app.aurio.domain.AppCategory
import app.aurio.domain.AppUsage
import app.aurio.domain.WeekChange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeInsightTest {
    private fun app(label: String, used: Int?, limit: Int = 30, category: AppCategory = AppCategory.LIMITED) =
        AppUsage(label.lowercase(), label, category, used, limit)

    private val better = listOf(WeekChange("ig", "Instagram", 20, 38, 5, 7, 2, 7))

    private fun insight(apps: List<AppUsage>, changes: List<WeekChange> = emptyList(), focusLeft: Int = 100) =
        homeInsight(apps, changes, focusLeft, focusMinutes = 25)

    @Test fun aLimitAboutToRunOutComesFirst() = assertEquals(
        "YouTube has 4 min left today. Staying under adds +20 at midnight.",
        insight(listOf(app("Instagram", 22), app("YouTube", 26), app("Snapchat", 50)), better),
    )

    @Test fun thenAWeekOfProgress() = assertEquals(
        "18 min less a day in your limited apps than the week before.",
        insight(listOf(app("Instagram", 5), app("Snapchat", 50)), better),
    )

    @Test fun thenALimitPassed() = assertEquals(
        "Snapchat is 20 min over its limit today. The loss stops at −60.",
        insight(listOf(app("Instagram", 5), app("Snapchat", 50))),
    )

    @Test fun thenFocusStillOpen() {
        assertEquals("A 25 min focus session adds +25 aura.", insight(listOf(app("Instagram", 5))))
        assertEquals("A 25 min focus session adds +10 aura.", insight(listOf(app("Instagram", 5)), focusLeft = 10))
    }

    @Test fun thenLimitsOnTrack() {
        assertEquals("All 2 limits are on track for +40 at midnight.", insight(listOf(app("A", 1), app("B", 2)), focusLeft = 0))
        assertEquals("Your limit is on track for +20 at midnight.", insight(listOf(app("A", 1)), focusLeft = 0))
    }

    @Test fun exactlyAtTheLimitIsOnTrack() =
        assertEquals("Your limit is on track for +20 at midnight.", insight(listOf(app("A", 30)), focusLeft = 0))

    @Test fun limitsNotCountedSayNothingAboutLimits() =
        assertEquals("A 25 min focus session adds +25 aura.", insight(listOf(app("Instagram", null), app("YouTube", null))))

    @Test fun neutralAppsAreIgnored() =
        assertNull(insight(listOf(app("Maps", 200, limit = 0, category = AppCategory.ESSENTIAL)), focusLeft = 0))
}
