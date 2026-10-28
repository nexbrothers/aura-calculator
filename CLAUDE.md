# Aurio

Android digital-wellbeing app for Gen Z (16–26). Intentional phone use becomes an "aura score" (0–1000).
Full product + visual brief: `docs/design-brief.md`. Approved interactive design: the "Aurio UI preview" canvas on claude.ai.

## Stack
- Kotlin 2.1, Jetpack Compose (BOM 2024.12), Material 3, Navigation Compose, Glance (widgets)
- minSdk 26, target/compile 35, JDK 17, single `:app` module, package `app.aurio`
- No DI framework yet; `AurioStore` (StateFlow) is the single source of truth

## Commands
- Build: `./gradlew :app:assembleDebug` (Windows: `gradlew.bat :app:assembleDebug`)
- Unit tests: `./gradlew :app:testDebugUnitTest`
- Lint: `./gradlew :app:lintDebug`
- Install on device: `./gradlew :app:installDebug`

## Layout
- `domain/AuraScoring.kt` – pure scoring rules + `AuraLevel`. No Android imports. Every rule change needs a test in `src/test/.../AuraScoringTest.kt`.
- `data/AurioStore.kt` – app state; `data/UsageAccess.kt` – UsageStatsManager + permission.
- `ui/theme/` – design tokens (`AurioColors`, `AuraPalettes`, `AurioSpacing`, `AurioRadius`, `AurioType`). Never hard-code colours or sizes in screens; add a token.
- `ui/components/` – `AuraField` (signature aura), `Pill`, `LimitRow`, `ScoreChangeRow`, buttons, `GroupedList`.
- `ui/home`, `ui/focus`, `ui/tabs` – screens. `ui/AurioApp.kt` – nav + bottom bar.
- `widget/` – Glance widgets.

## Non-negotiable product rules
1. Scoring (must match exactly): +1/focus minute on completion, max +100/day; +20 per limited app if the day ends under its limit (shown "projected" until midnight); −1/min over, max −60 per app per day; neutral/essential apps never change aura; Usage Access off → "Unknown", no bonus, no penalty. Levels: Static 0–199, Flicker 200–399, Glow 400–599, Radiant 600–799, Halo 800–1000.
2. Privacy: all data stays on the phone. Do NOT add the INTERNET permission, analytics, crash SDKs or network libraries without the owner's explicit approval. Read only foreground time per app, never content.
3. Ending a focus session early never costs aura. Plus never buys aura or erases a loss.

## Design rules
- Light mode only. Ink #17161C on paper #F6F6F3, white surfaces. Colour belongs to the aura; everything else stays quiet.
- Bricolage Grotesque (600–800) for numbers/headings, Onest for UI text. Sentence case, no emoji, no all-caps labels.
- Radii: hero 28dp, card 20dp, tile 12dp; pill buttons 54dp tall. 4dp grid, 20dp screen margins, ≥48dp touch targets.
- Never use colour alone: every status pill carries text. WCAG AA contrast (use `GainText`/`LossText`/`InkSubtle` for small text).
- Copy describes only what is measured: "You spent 45 min in Instagram", never "wasted".
- Respect reduced motion: pass `animate = false` to `AuraField` when animations are disabled.

## Roadmap (not built yet)
Splash, Plus paywall, focus foreground service.

## Working agreements for Claude
- Use the `aurio-design-system` skill before any UI work and the `compose-screen` skill for new screens.
- Run unit tests after touching `domain/`. Use the `aura-scoring-reviewer` agent for scoring changes and `privacy-auditor` before release work.

## Handoff
Start with docs/HANDOFF.md for current status, caveats and next steps.
