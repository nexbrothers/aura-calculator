---
name: compose-screen
description: Step-by-step recipe for adding a new Aurio screen in Jetpack Compose (route, state, UI, preview, test). Use when building any screen from the roadmap.
---

# Adding a screen to Aurio

1. **Read first**: the screen's section in `docs/design-brief.md`, and the `aurio-design-system` skill.
2. **State**: read from `AurioStore.state` with `collectAsStateWithLifecycle()`. Put any new rule in
   `domain/` as a pure function with a unit test. Mutations go through a named `AurioStore` function.
3. **File**: `ui/<feature>/<Name>Screen.kt`. Public `@Composable fun <Name>Screen(...)` takes callbacks
   for navigation (no NavController inside screens).
4. **Layout**: `statusBarsPadding()`, horizontal `AurioSpacing.xl`, vertical rhythm on the 4dp grid.
   Tab screens can reuse the `TabPage` pattern in `ui/tabs/TabScreens.kt`.
5. **Route**: register in `ui/AurioApp.kt`. Tab screens go in `Tab`; full-screen flows (focus, level up,
   paywall) are plain routes with no bottom bar.
6. **Accessibility**: `Role` on clickables, `contentDescription` on icon-only buttons and big numbers,
   `selectable` for choice cards.
7. **Preview**: add `@Preview(widthDp = 360, heightDp = 800, showBackground = true)` wrapped in `AurioTheme`.
8. **Verify**: `./gradlew :app:assembleDebug :app:testDebugUnitTest`. Fix all warnings you introduced.
9. Remove the item from the roadmap in `CLAUDE.md`.
