package app.aurio.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class DailyRecapTest {
    private val min = 60_000L

    private fun delayAt(local: String, minuteOfDay: Int, zone: String = "Asia/Kolkata"): Long {
        val z = ZoneId.of(zone)
        val now = LocalDateTime.parse(local).atZone(z).toInstant().toEpochMilli()
        return DailyRecap.millisUntilNext(now, z, minuteOfDay)
    }

    // ---- When ----

    @Test fun recapFollowsTheEndOfQuietHours() = assertEquals(7 * 60, DailyRecap.minuteOfDay(22 * 60, 7 * 60))

    @Test fun withoutQuietHoursItComesAtEight() = assertEquals(8 * 60, DailyRecap.minuteOfDay(600, 600))

    @Test fun lateEveningWaitsForTomorrowMorning() =
        assertEquals((9 * 60) * min, delayAt("2026-10-28T22:00", 7 * 60))

    @Test fun earlyMorningSendsTheSameDay() =
        assertEquals(90 * min, delayAt("2026-10-29T05:30", 7 * 60))

    @Test fun exactlyAtTheTimeMeansTomorrow() =
        assertEquals(24 * 60 * min, delayAt("2026-10-29T07:00", 7 * 60))

    @Test fun aShortSpringForwardNightStillLandsAtSeven() =
        // Europe/London moves clocks forward at 01:00 on 29 March 2026: the night is an hour shorter.
        assertEquals(7 * 60 * min, delayAt("2026-03-28T23:00", 7 * 60, "Europe/London"))

    // ---- What ----

    @Test fun aGainingDay() = assertEquals(
        DailyRecap.Message("Yesterday: +45", "Your aura is 412, Glow."),
        DailyRecap.message(delta = 45, totalAfter = 412, partial = false, previousTotal = 405),
    )

    @Test fun aLosingDayUsesARealMinus() = assertEquals(
        DailyRecap.Message("Yesterday: −12", "Your aura is 380, Flicker."),
        DailyRecap.message(delta = -12, totalAfter = 380, partial = false, previousTotal = 392),
    )

    @Test fun anEvenDay() = assertEquals("Yesterday: 0", DailyRecap.message(0, 150, false, 150).title)

    @Test fun aNewLevelIsNamed() = assertEquals(
        "Your aura is 412, Glow. You reached Glow.",
        DailyRecap.message(delta = 30, totalAfter = 412, partial = false, previousTotal = 382).text,
    )

    @Test fun droppingALevelIsNotCalledOut() = assertEquals(
        "Your aura is 395, Flicker.",
        DailyRecap.message(delta = -10, totalAfter = 395, partial = false, previousTotal = 405).text,
    )

    @Test fun aPartialDaySaysSo() = assertEquals(
        "Your aura is 100, Static. Part of the day was not counted.",
        DailyRecap.message(delta = 0, totalAfter = 100, partial = true, previousTotal = 100).text,
    )
}
