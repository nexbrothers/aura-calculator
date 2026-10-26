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

## Caveats
- Build verified locally on 2026-10-26: `assembleDebug` + 13 unit tests pass.
- JDK: Gradle 8.11 cannot run on JDK 25 (the default Java and both Android Studio JBRs here are 25).
  Use JDK 21: `JAVA_HOME=C:\Users\AmanSharma\.jdks\jbr-21.0.11`, and in Android Studio set
  Settings > Build Tools > Gradle > Gradle JDK to jbr-21.
- Always run `gradlew.bat :app:assembleDebug :app:testDebugUnitTest` after changes.
- Android Studio sync/Run needs a successful build to deploy; check the Build tab.
- DB tests are in `src/androidTest` (run on a device). Scoring/settlement tests are in `src/test`.

## Known gaps / next
1. Focus follow-ups: no in-app link to Android 14+ exact-alarm settings (notification can be minutes late);
   reopening after the alarm completed a session lands on Focus, not the complete screen; "left to earn" on the
   timer uses today even when the session will end tomorrow; zones with DST at midnight are untested.
2. Usage-access edge cases: access off is only noticed while the app or the 6-hour worker runs, so a short
   gap between runs goes unseen; Developer tools cannot simulate access off; DST/time-zone days are untested.
3. Level-up: celebrated only for levels reached at midnight (not right after a focus session). The share card was
   checked with the debug preview. App detail and the heatmap still need a look with real history.
4. Widgets are built but not yet looked at on a home screen.
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
