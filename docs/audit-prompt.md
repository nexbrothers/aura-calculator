# Aurio audit prompt

Paste everything below the line into a fresh agent session opened at the repository root.

---

# Aurio: full product, UI, UX, accessibility and performance audit

## Your role

Act as a senior Android product designer, UX researcher, accessibility specialist (TalkBack, WCAG 2.2 AA), Jetpack Compose architect and Android performance engineer.

You are auditing **Aurio**, an Android digital-wellbeing app for Gen Z (16–26). Intentional phone use becomes an "aura score" from 0 to 1000. The app is nearly feature-complete and is being prepared for production.

**This is an audit only. Do not change the app.** You may read code, build the app, install it on the emulator, use it, take screenshots and run read-only diagnostics. Then write one evidence-based report with a prioritised list of recommended changes. The owner will approve changes before anyone implements them.

## Read these first

1. `CLAUDE.md`: stack, layout, the non-negotiable product, privacy and design rules.
2. `docs/HANDOFF.md`: current status, known caveats and next steps.
3. `docs/design-brief.md`: product and visual brief.
4. `.claude/skills/aurio-design-system/`: tokens and component rules. This is the standard to audit against.
5. The approved prototype, if you can open it: the "Aurio UI preview" Design canvas on claude.ai (https://claude.ai/artifact/9f5Q3jnjdV2EWaFGEHq7LK). `project/App.dc.html` in it holds every screen. If you cannot open it, say so and audit against the brief and the design-system skill.

Treat the rules in `CLAUDE.md` as requirements. A screen that breaks one of them is a defect, not a matter of taste.

## The app at a glance

- **Stack:** Kotlin 2.1, Jetpack Compose (BOM 2024.12), Material 3, Navigation Compose, Glance widgets, Room (schema v2), DataStore, WorkManager, AlarmManager. minSdk 26, target 35. Single `:app` module, package `app.aurio`.
- **State:** `data/AurioStore.kt` (StateFlow) is the single source of truth; `data/AurioRepository.kt` and `data/db/` sit behind it.
- **Measurement:** `data/UsageAccess.kt` and `domain/ForegroundTime.kt` read foreground time from `UsageStatsManager.queryEvents`. Counting starts from the install moment.
- **Scoring:** pure logic in `domain/` (`AuraScoring`, `DayRules`, `Settlement`, `History`, `LevelUps`, `SettleSchedule`, `WidgetPicks`), with unit tests in `app/src/test`.
- **Design system:**
  - Tokens are in `ui/theme/` (`AurioColors`, `AuraPalettes`, `AurioSpacing`, `AurioRadius`, `AurioType`, `AurioElevation`).
  - Shared components are in `ui/components/`: `AurioCard`, `CardHeader`, `PillTabs`, `LimitStepper`, `Pill`, `AppTile`, `NotCountedNotice`, `StrokeIcon`/`AurioIcons`, `AuraField` and `RowDivider`.
- **Privacy promise:** no INTERNET permission, no analytics, no crash SDKs, no network libraries. Everything stays on the phone.

## Screen and flow inventory

Start from `ui/AurioApp.kt` (navigation), then confirm in code that nothing is missing from this list:

| Area | Route or entry | Source |
|---|---|---|
| Onboarding (5 steps: welcome, name, usage access, pick apps, done) | shown until `onboardingDone` | `ui/onboarding/` |
| Home: greeting, aura card, streak, level chip, not-counted notice, limits, today's changes | `home` | `ui/home/HomeScreen.kt` |
| Level sheet, streak sheet | from Home | `ui/home/` |
| Focus setup | `focus` | `ui/focus/FocusScreens.kt` |
| Focus running (live timer, end early) | `focus/run` | same |
| Session complete (particle burst, notification prompt) | `focus/done/{earned}` | same |
| Limits: Limited, Neutral and Essential tabs, rules card | `limits` | `ui/limits/LimitsScreen.kt` |
| Manage apps picker | `apps/edit`; the same picker is used in onboarding | `ui/apps/AppPicker*` |
| App detail: category, limit stepper, 7-day bars | `app/{pkg}` | `ui/apps/AppDetailScreen.kt` |
| Insights: last 7 days chart, today line by line, aura calendar | `insights` | `ui/insights/` |
| Profile: privacy, preferences, your data, edit-name and delete dialogs | `profile` | `ui/profile/ProfileScreen.kt` |
| Level-up moment and share card | `levelup` | `ui/levelup/` |
| Developer tools (debug builds only) | `debug`, `levelup/preview`, `sharecard/preview` | `ui/debug/` |
| Widgets: 2x2 aura, 4x1, 4x2 | home screen | `widget/` |
| Focus-end notification, exact alarm, settle job at midnight | system | `focus/`, `work/` |

For every screen record:
- its purpose and main actions;
- its components;
- what you observed;
- its issues;
- your recommendations.

If you cannot reach a screen or state, say so. Never assume it works.

## Environment and how to run it

- **Build:** `gradlew.bat :app:assembleDebug`. Gradle needs **JDK 21**: JDK 25 breaks the build with a loopback error.
  - Set `JAVA_HOME` for the command only.
  - Never change global config, git config or credentials.
- **Unit tests:** `gradlew.bat :app:testDebugUnitTest`.
- **Lint:** `gradlew.bat :app:lintDebug`. Read the HTML report.
- **Install:** `gradlew.bat :app:installDebug` onto the **emulator only** (the Pixel AVD). Check `adb devices` first. If a physical phone is connected, do not install on it or touch its data.
- **Screenshots:** use `adb exec-out screencap -p > file.png` and save them in your scratchpad. In Git Bash, prefix adb commands that use `/sdcard` paths with `MSYS_NO_PATHCONV=1`.
- **UI tree:** use `adb shell uiautomator dump`.
  - It hangs on the animated focus-running screen, so tap by coordinates there.
  - If a focus session is left running, end it before you move on.
- **Crash watch:** after each flow, keep `adb logcat -b crash` and `adb logcat | grep -i aurio` running for 30–45 seconds. A crash can arrive later than the first few seconds.
- **Reset data:** before you clear the app's data on the emulator (for example, to test onboarding or a fresh install), ask the owner. Do not delete data from the app's own Delete flow without asking.

## What to evaluate

### 1. Product rules compliance (check these first)

Check every screen that shows a number against `CLAUDE.md`:
- **Focus:** +1 per focus minute on completion, at most +100 a day.
- **Under limit:** +20 per limited app that ends the day under its limit, shown as "projected" until midnight.
- **Over limit:** −1 per minute over, at most −60 per app per day.
- **Neutral and essential apps:** never change the aura.
- **Usage Access off:** the day is not counted, with no bonus and no penalty.
  - The UI must explain this in plain words, not "Unknown".
  - The install day must never count against the user.
- **Levels:** Static 0–199, Flicker 200–399, Glow 400–599, Radiant 600–799, Halo 800–1000.
- **Ending focus early** never costs aura.

Report any screen, widget or notification where the number shown, the label or the wording disagrees with these rules. Use the `aura-scoring-reviewer` agent if you need a second opinion.

### 2. Privacy

- Run the `privacy-auditor` agent.
- Read `AndroidManifest.xml` (merged manifest too, from the build intermediates) and the dependency list.
- Confirm there is no INTERNET permission and no network or analytics library, including transitive ones.
- Confirm that only foreground time is read.
- Check what "Export my data" sends, and whether the copy on the Profile "Aurio sees / Never sees" cards is accurate.

### 3. Visual design and consistency

Audit against the design system, not against a different taste. The rules:
- Light mode only: ink #17161C on paper #F6F6F3, white surfaces.
- Colour belongs to the aura; everything else stays quiet.
- Bricolage Grotesque (600–800) for numbers and headings, Onest for UI text.
- Sentence case, no emoji, no all-caps labels.
- Radii: hero 28dp, card 20dp, tile 12dp. Pill buttons are 54dp tall.
- 4dp grid, 20dp screen margins, touch targets of at least 48dp.
- Every screen uses this layout pattern:
  - `Column(fillMaxSize().background(Paper).statusBarsPadding().verticalScroll(...).padding(start = xl, end = xl, top = md, bottom = xxl), spacedBy(lg))`;
  - white `AurioCard`s with soft shadows and the heading inside the card.

Look for:
- Hard-coded colours, sizes, dp values or font sizes in screens instead of tokens. Grep `Color(0x`, `.sp`, raw `dp` values in `ui/`.
- Raw Material 3 components that bypass shared components, for example `SingleChoiceSegmentedButtonRow`, flat `Card`/`Surface`, or plain `TextButton` "Back" links.
- Different page-header patterns, back affordances, card styles, divider styles, pill styles or empty states between screens.
- Default-styled `AlertDialog`s (edit name, delete all data) next to the custom UI.
- Text that breaks the copy rules: blame words such as "wasted", all caps, emoji, or vague or technical wording.
- Insets: content under the status bar or navigation bar, with both gesture and 3-button navigation.

Build a consistency matrix from real findings only:

| Pattern | Where it is right | Where it deviates | Recommended standard |
|---|---|---|---|

### 4. UX and usability

Walk through each journey as a first-time user, then as a returning user:

1. Install, onboarding, grant usage access, pick apps, first look at Home.
2. Refuse usage access during onboarding, then turn it on later from Home or Profile.
3. Start a focus session, leave the app, come back, finish it, see the completion and notification.
4. End a focus session early. Confirm it is clear that this costs nothing.
5. Add a limited app, change its limit, change its category, stop tracking it.
6. Open an app from Home and from Limits, read its detail page, go back.
7. Understand today's aura: why it went up or down, what is "projected", what the streak means.
8. Read Insights for the week and the calendar.
9. Change preferences, edit your name, export data, cancel a delete.
10. Use the widgets, including "Start focus" from a widget.

For each journey assess:
- whether the next step is obvious;
- the number of taps it takes;
- whether wording is clear;
- whether feedback and confirmation appear;
- whether you can recover from mistakes;
- whether back navigation is predictable;
- whether a new user understands what the aura is and what helps or hurts it.

### 5. Edge cases and states

Check in code, and on the emulator where it is safe:
- **First day:** no history, empty charts and calendar, a streak of 0.
- **Lists:** no limited apps; many limited apps (15 or more); very long app names; an app that is uninstalled after being tracked.
- **Usage Access:** turned off in the middle of a day, turned back on.
- **Time:** midnight rollover while the app is open; time-zone or manual clock change; device reboot (settle job and alarm).
- **Focus:** process death during a focus session; Doze; exact-alarm permission denied (Android 12+); notification permission denied (Android 13+).
- **Score limits:** score at 0 and at 1000; reaching the focus cap of 100; reaching the −60 penalty cap.
- **Font scale and display size:** font scale 1.3 and 2.0, largest display size, and a small screen (360dp wide). Look for clipping and truncation.
- **Forced dark:** system dark mode with the app in light-only mode. Check that nothing renders wrongly.

### 6. Accessibility (WCAG 2.2 AA, Android)

- Use TalkBack, if you can enable it on the emulator, on Home, Focus, Limits and Profile.
- Check:
  - content descriptions;
  - merged semantics on rows;
  - headings;
  - roles (button, switch, tab);
  - state descriptions for pills and toggles;
  - focus order;
  - announcements of live changes (the timer, score changes).
- Never use colour alone: every status pill needs text. Check gain/loss colours and `InkSubtle` small text for contrast.
- Touch targets of at least 48dp: stepper buttons, chevrons, chips, calendar arrows, the nav bar.
- Reduced motion:
  - `AuraField` gets `animate = false`;
  - the particle burst and the level-up animation are skipped;
  - the system "remove animations" setting is respected, not only the in-app toggle.
- Do not claim WCAG compliance from visual inspection alone. Say what you tested and how.

### 7. Performance

Measure where you can and label measurements as measured. Label code suspicions as suspected.

- **Cold start:** `adb shell am start -W -n app.aurio/.MainActivity`. Run it 3 times and report TotalTime.
- **Jank:** `adb shell dumpsys gfxinfo app.aurio reset`, scroll Home, Limits and Insights and run the focus timer, then `dumpsys gfxinfo app.aurio` (janky frames, 90th and 99th percentile).
- **Memory:** `adb shell dumpsys meminfo app.aurio` before and after a few minutes of use.
- **APK size:** the size of the APK, and whether R8, minify and resource shrinking are on for release.
- **Code review for:**
  - work on the main thread (Room, `UsageStatsManager`, `PackageManager` icon loading);
  - recomposition hot spots (the `AuraField` animation, the timer that ticks every second, state read too high in the tree);
  - unstable lambdas or params in large lists;
  - `LazyColumn` vs `Column` for long lists;
  - the cost and caching of the icon cache;
  - flows collected without lifecycle awareness;
  - WorkManager and AlarmManager scheduling (duplicates, wake-ups, battery);
  - widget update frequency.
- Recommend memoisation or a baseline profile only with a real reason.

### 8. Architecture and maintainability

Review:
- separation between `domain/`, `data/` and `ui/`;
- whether `AurioStore` is turning into a god object;
- duplicated components, and dead code (for example, leftovers in `ui/tabs/TabScreens.kt`);
- unused imports and resources;
- error handling around system APIs (null `className` in usage events, missing packages, `SecurityException`);
- test coverage of the rules in `domain/` against `CLAUDE.md`;
- whether lint, ktlint or Detekt and CI exist.

Prefer small, incremental fixes over rewrites.

## Known context (verify, do not re-report as new discoveries)

- Widgets use the system font because Glance cannot load custom fonts. Treat this as a known platform limitation unless you can show a workaround.
- Developer tools is debug-only and never ships. Audit it lightly.
- Light mode only is a deliberate product decision.
- Not yet built (roadmap): splash screen, Plus paywall, limit-warning notifications, focus foreground service. Note gaps these leave, but do not report them as bugs.
- The owner believes the five main tabs are now consistent, and that App detail, the Manage apps picker and Onboarding still use an older flat style. Confirm or correct this with evidence.

## Rules of engagement

1. Do not modify, create or delete any file in the repository. Write notes, screenshots and the report in your scratchpad only, until the owner says where to save it.
2. Do not commit, push, change branches or touch git config or credentials.
3. Do not add dependencies, permissions or network access, even temporarily.
4. Use the emulator only. Ask before clearing app data. Never use a connected physical phone.
5. Do not invent screenshots, test results or metrics. If you did not run it, say so.
6. Separate verified defects from risks and from subjective preferences, and label each one.
7. Do not recommend a different aesthetic where the existing design system is coherent. Preserve what works and say so.
8. Avoid duplicate findings. Merge them and list every place they occur.
9. At the end, run `git status` and confirm the working tree is unchanged.

## Findings format

```
### UI-001: short title
- Category: Product rules / Privacy / UI / UX / Consistency / Accessibility / Performance / Architecture
- Where: screen + file:line
- Priority: P0 / P1 / P2 / P3
- Confidence: High / Medium / Low
- Verified or suspected: Verified (how) / Suspected (why)
- Problem:
- Evidence: screenshot ID, code reference, measurement, or log line
- User impact:
- Recommended fix: the smallest change that solves it
- Effort: S / M / L
- Depends on:
```

Priorities:
- **P0:** breaks a product or privacy rule, causes a crash, loses data, or shows a wrong score.
- **P1:** a major usability or accessibility barrier, a broken important flow, or verified jank.
- **P2:** a consistency problem, avoidable friction, or a moderate performance concern.
- **P3:** polish.

## Report structure

1. **Executive summary:** overall verdict, strongest parts, biggest weaknesses and risks, top five fixes.
2. **Inventory:** every screen, sheet, dialog, widget and notification, each marked inspected (how) or not inspected (why).
3. **Product rules and privacy compliance.**
4. **UI audit**, with screenshot references (ID, screen, device, font scale).
5. **UX audit**, journey by journey.
6. **Consistency matrix.**
7. **Accessibility:** what was tested and what was found.
8. **Performance:** measured results, then suspected bottlenecks.
9. **Architecture and maintainability.**
10. **Findings register:** every finding, sorted by priority.
11. **Design-system gaps:** missing tokens or components that would prevent the issues found. Extend the existing system; do not replace it.
12. **Roadmap:**
    - Phase 1: P0 fixes.
    - Phase 2: high-impact UX, accessibility and consistency.
    - Phase 3: components and refactoring.
    - Phase 4: polish.

    Give each item its effort and dependencies.
13. **Verdict:**
    - Is it ready for production?
    - Is it intuitive and consistent?
    - Are there performance concerns?
    - What should be fixed first?
    - What should stay exactly as it is?
    - What is the smallest set of changes with the biggest gain?

Be direct. The owner wants the truth about the app, not reassurance. Do not implement anything.
