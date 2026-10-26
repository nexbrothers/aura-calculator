package app.aurio.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WidgetPicksTest {
    private fun limited(label: String, used: Int?, limit: Int) = AppUsage(label.lowercase(), label, AppCategory.LIMITED, used, limit)

    @Test fun furthestOverComesFirstThenLeastTimeLeft() {
        val apps = listOf(limited("Instagram", 23, 30), limited("YouTube", 47, 30), limited("X", 14, 15), limited("Snapchat", 35, 30))
        assertEquals(listOf("YouTube", "Snapchat", "X", "Instagram"), WidgetPicks.limitsByUrgency(apps).map { it.label })
    }

    @Test fun neutralAndEssentialAppsAreLeftOut() {
        val apps = listOf(
            AppUsage("maps", "Maps", AppCategory.NEUTRAL, 300, 0),
            AppUsage("wa", "WhatsApp", AppCategory.ESSENTIAL, 300, 0),
            limited("Instagram", 5, 30),
        )
        assertEquals(listOf("Instagram"), WidgetPicks.limitsByUrgency(apps).map { it.label })
    }

    @Test fun unknownAppsComeLastAndAreNeverRanked() {
        val apps = listOf(limited("Zomato", null, 30), limited("Instagram", 1, 30), limited("Arattai", null, 30))
        assertEquals(listOf("Instagram", "Arattai", "Zomato"), WidgetPicks.limitsByUrgency(apps).map { it.label })
    }

    @Test fun closestLimitIsTheMostUrgent() {
        assertEquals("YouTube", WidgetPicks.closestLimit(listOf(limited("Instagram", 10, 30), limited("YouTube", 29, 30)))?.label)
    }

    @Test fun noLimitedAppsMeansNothingToShow() {
        assertNull(WidgetPicks.closestLimit(emptyList()))
    }
}
