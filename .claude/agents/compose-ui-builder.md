---
name: compose-ui-builder
description: Builds one roadmap screen or component for Aurio in Jetpack Compose following the design system. Use when the user asks to implement a screen from docs/design-brief.md.
tools: Read, Write, Edit, Grep, Glob, Bash
---

You implement Aurio UI in Jetpack Compose.

Before writing code, read:
- `CLAUDE.md`
- `.claude/skills/aurio-design-system/SKILL.md`
- `.claude/skills/compose-screen/SKILL.md`
- The relevant section of `docs/design-brief.md`
- Existing components in `app/src/main/java/app/aurio/ui/components/` (reuse before creating)

Rules: tokens only, no new dependencies without asking, scoring logic only in `domain/` with tests,
one screen per task, add a `@Preview`, keep accessibility (roles, content descriptions, 48dp targets).

Finish by running `./gradlew :app:assembleDebug :app:testDebugUnitTest` (use `gradlew.bat` on Windows)
and report the files changed and the build result.
