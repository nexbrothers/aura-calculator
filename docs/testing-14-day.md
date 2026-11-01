# Waura 14-day test

Build: 0.2.0 (versionCode 4). Goal: find out whether Waura improves habits, not how often it is opened.

## What we want to learn
1. Do testers keep their limits more often in week 2 than in week 1?
2. Do Aura rescue reminders help (limit kept after a reminder)?
3. Are focus sessions used again after the first one?
4. Do habits improve even on days the app is not opened?

## Before handing out the build (owner)
- [ ] Upload `app/build/outputs/bundle/release/app-release.aab` to Play Console internal testing
      (or share `app-release.apk` directly). Both are signed with the upload key.
- [ ] Install it on your own phone first and run the accuracy check below for 3 days.
- [ ] Recruit 5 to 15 testers aged 16 to 26 on Android 8 or newer.

## Tester setup (day 1)
1. Install Waura and finish setup. Allow usage access and notifications when asked.
2. Mark 2 to 4 apps you want to use less as Limited, with honest limits.
3. Leave Aura rescue and Limit warnings on (Profile > Notifications).
4. Use the phone as normal. There is nothing to do daily; opening Waura is optional.

Note for testers: apps added after day 1 start counting the next day. This is on purpose.

## Accuracy check (owner, 3 days, before testers start)
Each evening, compare Waura's minutes with Android's own numbers:
- Android: Settings > Digital Wellbeing & parental controls (or Screen time) > today's app list.
- Waura: Limits tab, minutes per app.
- Write both down. Differences of 1 to 2 minutes are normal. Larger gaps: note the app and
  what you did (picture-in-picture video, calls, split screen are known weak spots).

| Day | App | Android min | Waura min | Note |
|-----|-----|-------------|-----------|------|
|     |     |             |           |      |

## Collecting results (day 15)
Ask each tester to open Profile > Export my data and send it to you through any app they like.
The first block, `progressSummary`, has the answers:
- `weeks[0]` vs `weeks[1]`: `limitDaysKept` / `limitDaysCounted`, `minutesOverLimits`,
  `averageMinutesPerLimitedApp`, `focusSessionsCompleted`, `daysWithFocus`.
- `rescueReminders`: `sent`, `limitKeptAfter`, `limitPassedAfter`.
Testers may share only that block if they prefer; the rest lists their tracked apps.

Also ask three short questions:
1. Did a reminder ever stop you at the right moment? When?
2. Did anything feel unfair about the score?
3. Would you keep Waura installed? Why or why not?

## Known limits of this build
- Aura rescue reminders arrive silently (low-importance channel) and learn for at least 4 days.
- Usage that happens while usage access is off is never counted (shown as "Unknown").
- Days settle just after midnight; history needs the phone to be on around then or the app opened later.
