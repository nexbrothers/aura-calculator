---
name: aurio-design-system
description: Aurio's visual language and Compose tokens. Use before creating or changing any Aurio screen, component, widget or copy.
---

# Aurio design system

Source of truth in code: `app/src/main/java/app/aurio/ui/theme/` (Tokens.kt, Theme.kt). Brief: `docs/design-brief.md`.

## The one bold thing
The **aura** (`ui/components/AuraField.kt`): a grainy field of drifting blobs whose colours follow the level.
Spend boldness only there. Everything else is quiet: ink on paper, white surfaces, soft depth.

| Level | Palette token | Feel |
|---|---|---|
| Static 0–199 | `AuraPalettes.Static` | cool greys |
| Flicker 200–399 | `AuraPalettes.Flicker` | orange, coral |
| Glow 400–599 | `AuraPalettes.Glow` | yellow, mint |
| Radiant 600–799 | `AuraPalettes.Radiant` | pink, yellow, mint, orange |
| Halo 800–1000 | `AuraPalettes.Halo` | mint, sky, lilac |

## Tokens (use these, never raw values)
- Colour: `AurioColors.Ink / InkMuted / InkSubtle / Paper / Surface / SurfaceSunken / Track / Divider`;
  semantic `Gain/GainText/GainBg`, `Loss/LossText/LossBg`, `Unknown/UnknownBg`, `Neutral/NeutralBg`.
- Type: `AurioType.scoreHero, scoreLarge, display, title, body, bodyStrong, label, caption, button`.
- Spacing (4dp grid): `AurioSpacing.xs 4, sm 8, md 12, lg 16, xl 20 (screen margin), xxl 24, xxxl 32`, `buttonHeight 54`, `touchMin 48`.
- Radius: `AurioRadius.hero 28, card 20, tile 12`; buttons and pills are fully rounded.

## Component rules
- Primary action: `PrimaryButton` (black pill). Secondary: `SecondaryButton`.
- Status: `Pill` with a `PillTone`. Every pill has text; never signal state with colour alone.
- Lists: `GroupedList` + `RowDivider`. Do not wrap every row in its own card.
- Apps: `AppTile` (pastel square + initial). Never real brand logos.
- Numbers: Bricolage, tight tracking, use `signed()` for +/− (real minus sign U+2212).

## Copy
Sentence case, plain verbs, no emoji, no all-caps, no "→" on buttons. Buttons say what happens ("Start focus session").
Describe only what is measured ("47 of 30 min used"), never judge ("wasted"). Errors explain what to do next.

## Motion
One orchestrated moment per screen at most. Allowed: aura drift, score tick-up + brighten on gain, dim on loss,
celebration burst on session complete / level up. Respect reduced motion.

## Checklist before finishing UI work
- [ ] Only tokens used; no new hex/dp literals outside `ui/theme`
- [ ] Touch targets ≥ 48dp, content descriptions on icon-only controls
- [ ] Text contrast AA (small grey text uses `InkSubtle`, not lighter)
- [ ] Works at 360dp width with 20dp margins, edge-to-edge insets handled
- [ ] `@Preview` added for the new composable
