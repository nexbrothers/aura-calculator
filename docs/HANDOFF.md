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
1. Notification when a focus session ends (alarm + channel; ask POST_NOTIFICATIONS after first completed session).
2. Usage-access edge cases: access off is only noticed while the app or the 6-hour worker runs, so a short
   gap between runs goes unseen; Developer tools cannot simulate access off; DST/time-zone days are untested.
3. Level-up screen / share card, heatmap, 7-day bars per app detail.
4. Widgets (2x2, 4x1, 4x2) read from the store and refresh on session and midnight; currently 2x2 shows score only.
5. Room migration tests; usage accuracy check against Android's own screen time for 3 days.
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
  No full reading (access off at settlement, or the day is more than 7 days old) means Unknown.
