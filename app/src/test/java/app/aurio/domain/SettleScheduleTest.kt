package app.aurio.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class SettleScheduleTest {
    private val min = 60_000L

    private fun delayAt(local: String, zone: String): Long {
        val z = ZoneId.of(zone)
        val now = LocalDateTime.parse(local).atZone(z).toInstant().toEpochMilli()
        return SettleSchedule.millisUntilNextRun(now, z)
    }

    @Test fun eveningRunsJustAfterMidnight() {
        assertEquals((2 * 60 + 5) * min, delayAt("2026-10-26T22:00", "Asia/Kolkata"))
    }

    @Test fun justAfterMidnightButBeforeTheRunWaitsMinutesNotADay() {
        assertEquals(3 * min, delayAt("2026-10-27T00:02", "Asia/Kolkata"))
    }

    @Test fun rightAtTheRunTimeSchedulesTomorrow() {
        assertEquals(24 * 60 * min, delayAt("2026-10-27T00:05", "Asia/Kolkata"))
    }

    @Test fun shortDaylightSavingNightStillLandsAfterMidnight() {
        // Clocks go forward at 02:00 on 2026-03-29 in Berlin; midnight itself is unaffected.
        assertEquals((60 + 5) * min, delayAt("2026-03-28T23:00", "Europe/Berlin"))
    }

    @Test fun longDaylightSavingDayWaitsTwentyFiveHours() {
        // Clocks go back at 03:00 on 2026-10-25 in Berlin, so that day is 25 hours long.
        assertEquals(25 * 60 * min, delayAt("2026-10-25T00:05", "Europe/Berlin"))
    }
}
