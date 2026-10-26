package app.aurio.domain

/** One frozen ledger line produced when a day settles. */
data class SettledEntry(val kind: LineKind, val packageName: String?, val points: Int, val note: String)

data class SettledDay(
    val entries: List<SettledEntry>,
    /** Actual change after clamping to 0..1000. */
    val delta: Int,
    val totalAfter: Int,
    val partial: Boolean,
    val streakAfter: Int,
    val levelFrom: AuraLevel,
    val levelTo: AuraLevel,
)

/**
 * Turns one finished day's facts into frozen points. Pure: no Android, no clock, no database.
 * Rules come from AuraScoring so the live "today" view and settlement can never disagree.
 */
object Settlement {

    fun settle(
        apps: List<AppUsage>,
        completedSessionMinutes: List<Int>,
        previousTotal: Int,
        previousStreak: Int,
        isFirstDay: Boolean,
    ): SettledDay {
        val entries = mutableListOf<SettledEntry>()
        val earnings = AuraScoring.focusEarnings(completedSessionMinutes)
        completedSessionMinutes.zip(earnings).forEach { (minutes, earned) ->
            if (earned > 0) entries += SettledEntry(LineKind.FOCUS, null, earned, "Focus session, $minutes min")
        }

        val limited = apps.filter { it.category == AppCategory.LIMITED }
        var anyUnknown = false
        var allUnder = limited.isNotEmpty()
        limited.forEach { app ->
            when (val status = AuraScoring.limitStatus(app)) {
                is LimitStatus.Under -> entries += SettledEntry(LineKind.LIMIT_BONUS, app.packageName, status.projectedBonus, "${app.label} under limit")
                is LimitStatus.Over -> {
                    allUnder = false
                    entries += SettledEntry(LineKind.LIMIT_PENALTY, app.packageName, -status.penalty, "${app.label} over limit")
                }
                else -> { anyUnknown = true; allUnder = false }
            }
        }

        val raw = previousTotal + entries.sumOf { it.points }
        val total = raw.coerceIn(0, AuraScoring.MAX_SCORE)
        // A streak day is intentional use: a finished focus session, or every limit kept.
        val qualifies = completedSessionMinutes.isNotEmpty() || allUnder
        return SettledDay(
            entries = entries,
            delta = total - previousTotal,
            totalAfter = total,
            partial = anyUnknown || isFirstDay,
            streakAfter = if (qualifies) previousStreak + 1 else 0,
            levelFrom = AuraLevel.of(previousTotal),
            levelTo = AuraLevel.of(total),
        )
    }
}
