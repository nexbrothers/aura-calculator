# Aurio handoff (read this first in a new Claude Code session)

Plans (Claude Docs, read-only context):
- Production plan: https://claude.ai/artifact/9g5A8fimCWbopt4H657ovp
- Local-first development plan (M1 to M10): https://claude.ai/artifact/Jaq6DiShvTEKbtMcfSpDGS
- Design prototype: https://claude.ai/artifact/9f5Q3jnjdV2EWaFGEHq7LK
- Product and design rules: docs/design-brief.md and CLAUDE.md

## What works now (all on-device, no INTERNET permission)
- Room database (7 tables) + DataStore settings; `AurioStore` exposes `AurioState` derived from them.
- Onboarding (5 steps, resumes), app picker (Limited / Neutral / Essential), Limits with steppers, Manage apps.
- Focus: presets 25/50/90 plus Custom 5 to 180 min; end time saved; overdue session completed on next open.
- Midnight settlement (`domain/Settlement.kt`, `AurioRepository.settlePastDays`, `work/SettleWorker.kt`) freezes days:
  entries, daily_score, streak, level events. Pure scoring rules live in `domain/AuraScoring.kt`.
- Insights from real history, Profile (name, default length, reduce motion, export JSON, delete all data).
- Debug-only Developer tools (Profile > Developer tools): time travel (`DebugTime`), fake usage, finish session.
- Limit warnings (`limits/LimitWarnings.kt`, rules in `domain/LimitWatch.kt`): "5 min left" and "limit reached",
  once per app per day, silent in quiet hours, lock screen shows only "A limit is close". No foreground service:
  each check books the next alarm at the soonest moment a threshold could be crossed (usage grows at most one
  minute per minute). Exact when exact alarms are allowed, otherwise Android may run it minutes late. Re-checked
  on sync, limit edits, midnight, boot. Permission asked once on Limits. Checked on the emulator with fake usage.
- Profile > Notifications: session complete and limit warnings switches, quiet hours (half-hour steps, equal
  times = off), a "Notifications are off" row when blocked, and "Precise timing" (Android 12+) opening the
  exact-alarm setting. System states are re-read whenever Profile resumes.
- Daily recap (`recap/DailyRecapNotifier.kt`, wording and timing in `domain/DailyRecap.kt`): off by default; one
  inexact alarm each morning when quiet hours end (8:00 with quiet hours off), settles first, then posts
  yesterday's final delta, total, a new level and a partial-day note. Low-importance channel, opens Insights.
- Usage-access-off notice (`limits/AccessNotice.kt`): once per off spell when limits are set and Aurio is not on
  screen; checked by limit checks and the 6-hour settle job; cleared when access is back.
- Developer tools > Notifications: "Send daily recap now" and "Run a limit check in 10 seconds" (leave the app).

## Caveats
- Build verified locally on 2026-10-26: `assembleDebug` + 13 unit tests pass.
- JDK: Gradle 8.11 cannot run on JDK 25 (the default Java and both Android Studio JBRs here are 25).
  Use JDK 21: `JAVA_HOME=C:\Users\AmanSharma\.jdks\jbr-21.0.11`, and in Android Studio set
  Settings > Build Tools > Gradle > Gradle JDK to jbr-21.
- If Gradle fails with "Unable to establish loopback connection" even on JDK 21, set
  `JAVA_TOOL_OPTIONS=-Djdk.net.unixdomain.tmpdir=<a folder inside the project, e.g. build\udstmp>` for that command.
- Always run `gradlew.bat :app:assembleDebug :app:testDebugUnitTest` after changes.
- Android Studio sync/Run needs a successful build to deploy; check the Build tab.
- DB tests are in `src/androidTest` (run on a device). Scoring/settlement tests are in `src/test`.

## Known gaps / next
1. Focus follow-ups: reopening after the alarm completed a session lands on Focus, not the complete screen; "left to earn" on the
   timer uses today even when the session will end tomorrow; zones with DST at midnight are untested.
2. Usage-access edge cases: access off is only noticed while the app or the 6-hour worker runs, so a short
   gap between runs goes unseen; Developer tools cannot simulate access off; DST/time-zone days are untested.
3. Level-up: celebrated only for levels reached at midnight (not right after a focus session). The share card was
   checked with the debug preview. App detail and the heatmap still need a look with real history.
4. Widgets checked on a realme 9 home screen (render, Start focus opens Focus, tap opens the app). Their text uses
   the system font (Glance cannot load Bricolage); render the score into the aura bitmap if that matters.
5. Run device tests (connectedDebugAndroidTest). Compare Aurio's minutes with the phone's own screen-time page
   for 3 days now that usage is read from events. Known small gaps: picture-in-picture is not counted, and a
   screen woken without a new resume (call proximity blanking) loses the time after wake.
6. Open rule decisions (recommendations in use): streak = focus session OR all limits kept; usage-off day = Unknown;
   phone-free = timer completion; session crossing midnight credits the end day.
7. Product question: a Limited app is scored from the day it is added, and removal applies tomorrow, so adding
   an unused app late and removing it after midnight earns two days of +20. Consider a per-day bonus cap or
   scoring new apps from tomorrow.

## Day terms (schema 2)
- usage_day records label, category and limit per app per day the first time the app is seen that day.
- DayRules: lowering a limit applies today; raising, category changes and removal apply tomorrow.
- Settlement and Home both score from recorded terms; days with no record fall back to current settings.

## Counting starts at install
- markStartDate records start_date and start_at (the set-up moment). The install day is measured from start_at,
  not midnight (DayRules.measureFrom; UsageAccess notBefore), so time before Aurio existed never counts.
- The access-off mark never applies to the install day (DayRules.accessOffCounts): access is turned on during
  set-up, and once on, Android's record since install is read in full. Installs from before start_at existed fall
  back to when their first apps were chosen.

## Unknown days
- If Usage Access is seen off at any point on a day (app_meta key access_off:<date>, set by sync and SettleWorker),
  that day is Unknown: Home shows Unknown and settlement never reads Android for it.
- Otherwise a day settles from Android's full-day reading, never below the minutes Aurio saved that day.
  No full reading (access off at settlement, or the day is more than 6 days old) means Unknown.

## Focus end and midnight
- FocusAlarm books an alarm at the session end (exact if allowed). FocusEndReceiver completes an overdue session
  and posts "Session complete" only if it completed it and Aurio is not on screen. Re-armed on boot and app open.
- Completing, ending and settling share one lock; settlement completes overdue sessions first. A session is never
  credited to a settled day (goes to the next open day). End after the time is up completes the session.
- MidnightAlarm (inexact, 00:05 local) starts SettleWorker and re-books itself; also re-booked on boot, clock and
  time-zone change. A 6-hour periodic SettleWorker is the backup.

## App detail and history
- Route app/{pkg}, opened from Limits and Home. 7-day bars use domain/History.week; each bar is judged by the
  limit that applied that day. Settlement now writes each day's final minutes (or Unknown) back to usage_day,
  so history shows what the score came from.
- Insights has a month calendar (ui/insights/AuraCalendar.kt) tinted by the level each settled day ended on.

## Level-up
- Settlement writes level_event rows. LevelUps.toCelebrate picks the highest unseen rise still held; AurioStore.levelUp
  exposes it and MainNav opens the "levelup" route once, on a main tab. app_meta level_seen_through marks it seen.
- ShareCard draws a 1080x1920 PNG (aura, level, score only), shares it via FileProvider (cache/share only).
  Delete all data removes it. Developer tools > Preview level-up / Preview share card show both with sample
  values (nothing saved or shared). Card text stays inside the story safe area (top 250, bottom 340 px).

## Widgets
- widget/AuraWidget.kt: 2x2 (aura bitmap, level, score, today), 4x1 (closest limit + Start focus), 4x2 (aura + 3 limits).
  All read AurioStore.state, so they match Home. The aura is drawn by ui/aura/AuraPainter (shared with ShareCard).
- Redrawn when score, level, today or limits change (AurioStore), and every ~30 min the receivers also sync usage.
- Start focus opens MainActivity with EXTRA_OPEN=focus, routed to the Focus tab.

## Usage minutes
- Read from UsageStatsManager.queryEvents (6 h lookback), summed by domain/ForegroundTime.kt and cut exactly
  at local midnight. Only RESUMED/PAUSED, screen off, shutdown and startup events are used; STOPPED is ignored.
- Activity names are hashed immediately in UsageAccess; nothing but minutes per package is stored.
