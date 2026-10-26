# Aurio

Your attention has an aura. An on-device digital-wellbeing app for Android, built with Jetpack Compose.

## Run it
1. Open this folder in **Android Studio** (Ladybug 2024.2 or newer) → *Open* → select `Aurio`.
2. Let Gradle sync (first sync downloads Gradle 8.11.1, AGP 8.7.3 and the Android SDK 35 if missing).
3. Pick an emulator or device (Android 8.0+) and press **Run**.
4. In the app, Profile → Usage access → allow Aurio to see real usage minutes.
   Without it, limits show as "Unknown", exactly as designed.

Command line: `gradlew.bat :app:assembleDebug` and `gradlew.bat :app:testDebugUnitTest`.

## What's built
- Design tokens mapped to a Compose theme (colours, aura palettes per level, Bricolage Grotesque + Onest, spacing, radii)
- Animated grainy `AuraField`
- Home (aura header, score tick-up, streak, focus cap, limits, "What changed today", aura levels sheet)
- Focus setup → running countdown → session complete (score updates)
- Limits (Limited / Neutral / Essential, steppers, rules), Insights (weekly bars, today line by line), Profile & privacy
- Real Usage Access via `UsageStatsManager`, no INTERNET permission
- 2×2 Glance home-screen widget
- Unit-tested scoring rules (`AuraScoringTest`)

See the roadmap in `CLAUDE.md` for what comes next.

## Working with Claude
- `CLAUDE.md` – project memory: stack, commands, rules
- `.claude/skills/` – `aurio-design-system`, `compose-screen`, `android-build-verify`
- `.claude/agents/` – `compose-ui-builder`, `aura-scoring-reviewer`, `privacy-auditor`
- `docs/design-brief.md` – full product and design brief

Fonts: Bricolage Grotesque and Onest, SIL Open Font License (Google Fonts).
