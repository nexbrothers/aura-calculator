Design a complete, production-ready mobile app UI for "Aurio", an Android digital-wellbeing app for Gen Z (ages 16–26). It should look and feel like a flagship app from a top consumer tech company: the polish of Apple Fitness, Headspace, CRED and Spotify Wrapped, but built on Android Material 3 conventions. This is the real shipping app design, not a wireframe or a web page.

PRODUCT
Aurio turns intentional phone use into a personal "aura score" (0–1000 points). Users earn aura for phone-free focus sessions and for staying within the daily limits they set for distracting apps (Instagram, YouTube, Snapchat, X). They lose aura when they go over a limit. All usage data and scoring stay on the phone. The app never reads messages, photos or content inside apps; it only knows how long each app was open.

Scoring rules (show these accurately in the UI):
- +1 aura per phone-free focus minute, added when a session completes (max +100 a day)
- +20 when the day ends under an app's limit (shown as "projected" until midnight)
- −1 per minute over a limit (max −60 per app per day)
- Neutral and essential apps (Maps, WhatsApp, calls) never change aura
- If Usage Access is off, the period shows as "Unknown": grey, no bonus, no penalty
- Five aura levels: Static (0–199), Flicker (200–399), Glow (400–599), Radiant (600–799), Halo (800–1000)

PLATFORM AND FORMAT
- Android, 360 × 800 dp frames, Material 3 components, edge-to-edge with status bar and gesture navigation bar
- Light mode only
- Bottom navigation with 5 tabs: Home, Focus, Limits, Insights, Profile
- Use realistic content throughout (user name "Riya", real app names, real numbers); no lorem ipsum
- Do not use real brand logos; show apps as rounded-square tiles with the app's initial on a soft pastel colour

VISUAL DIRECTION
- Signature element: the AURA. A soft, glowing, grainy gradient colour field, inspired by aura photography, with slowly drifting blobs of pink, orange, yellow, mint, sky blue and lilac. The aura's colours change with the user's level:
  Static = cool greys; Flicker = orange and coral; Glow = yellow and mint; Radiant = pink, yellow, mint and orange; Halo = mint, sky blue and lilac.
- Everything else is calm and premium: near-black ink (#17161C) on a soft off-white (#F6F6F3) with white cards (#FFFFFF). Colour belongs to the aura; the rest of the UI stays quiet so the aura stands out.
- Semantic colours: gain green #0E9467 on #E2F6EC, loss red #E0402F on #FDE8E5, unknown grey #8A8894 on #EEEDF1
- Typography: Bricolage Grotesque (700–800) for big numbers and headings, Onest for body and UI text. Big, confident numerals with tight letter-spacing.
- Shapes: 28 dp radius for hero cards, 20 dp for cards, 12 dp for app tiles, fully rounded pill buttons (54 dp tall, black primary). 4 dp spacing grid, 20 dp screen margins, 48 dp minimum touch targets.
- Subtle depth only (soft shadows, no heavy borders), generous whitespace, sentence case everywhere, no emoji, no all-caps labels.

SCREENS TO DESIGN

1. Splash: the aurio wordmark on a full-screen animated aura.

2. Onboarding (4 steps with a progress bar):
   a) Welcome: large aura in the top half, headline "Your attention has an aura.", a "Get started" button
   b) Privacy promise: "Everything stays on this phone." with 3 icon rows: Scored on your phone / Time only, never content / No account, no uploads
   c) Pick your apps: a 3×3 grid of app tiles with a selected state (black outline and check); button "Set 4 daily limits"
   d) Usage access: a mock of the Android Settings toggle for Aurio, an "Open Settings" button and a "Not now" option. Note: "Without it, limits show as unknown, never as a win or a loss."

3. HOME (most important screen, make it stunning):
   - Greeting: "Good evening, Riya", the date, and a profile avatar
   - HERO AURA CARD: a large card filled with the living aura gradient. It shows the level name "Radiant", a huge score "705", a pill "+93 today", and a progress bar to the next level with "95 to Halo"
   - A streak chip ("6-day streak") and today's focus progress ("50 of 100 focus aura")
   - Primary button "Start focus session"
   - "Today's limits" card: each app shows a tile, name, progress bar and a status such as "7 min left" (YouTube is red: "17 min over")
   - "What changed today" feed: a list of score changes with icons and short reasons, for example "+25 Focus session, 25 min", "−17 YouTube over limit"
   - Small "Aura level" sheet preview showing all 5 levels

4. Focus setup: duration choices (25 / 50 / 90 min) as large selectable cards showing "+25 aura" etc., an optional ambient sound toggle, and a "Start" button.

5. Focus running: a full-screen aura background, a large circular countdown ring "15:47", the line "left to earn +25", status pills "Notifications paused" and "No ads", the note "Ending early never costs aura", and an "End session" button.

6. Session complete: a celebration screen with a glowing aura orb, a huge "+25", "Session complete.", the new score "Your aura is now 730", a focus-cap progress card, and "Back to home" / "Start another" buttons. Include a confetti-like aura particle burst.

7. Level up moment: a full-screen takeover when the user reaches a new level ("You reached Halo"), with the aura colours morphing and a "Share" button that creates an Instagram-story-style share card (score plus aura, no usage data).

8. Limits: a segmented control (Limited / Neutral / Essential) and app cards with a −/+ limit stepper, used-time slider and status pill ("+20 at midnight", "−17 today"). Expanded state for one app, "Add app" action, and a rules explainer card.

9. App detail (for example YouTube): today's usage against the limit, a 7-day usage bar chart, the limit setting, a category switch, and an aura impact summary.

10. Insights: a weekly aura change bar chart (positive bars green, negative red, today striped as projected), "+231 this week", best day, total focus time, and a "Today, line by line" list with a total. Add a monthly calendar heatmap view tinted by aura colour.

11. Profile and privacy: Usage access toggle, cloud backup toggle (off by default), an "Aurio sees / Never sees" two-column card, notification settings, aura theme picker, "Delete all data" in red, and app version.

12. Aurio Plus paywall: a Halo aura header, benefits (No ads, Full history, More themes and widgets), Yearly and Monthly plan cards with "Price at launch" placeholders, the line "Plus never buys aura and never erases a loss.", and a "Continue with Plus" button.

13. Home-screen widgets on an Android wallpaper: a 2×2 aura widget (score plus level gradient), a 4×1 widget (closest limit plus a "Start focus" button), and a 4×2 widget (score plus today's limits).

14. Notifications: a lock-screen notification "Instagram: 5 min left in today's limit" and a session-complete notification.

STATES AND DETAILS
- Empty state (new user, no sessions yet), the Usage-access-off state (grey "Unknown" chips everywhere), a loading skeleton, and an error toast
- Score-change micro-interactions: numbers tick up, the aura brightens on gain, and the card dims slightly on loss
- Accessibility: WCAG AA contrast; never use colour alone (always pair it with text or icons)
- Copy tone: friendly, plain, honest; describe only what is measured ("You spent 45 min in Instagram", never "You wasted 45 min on Reels")

DELIVERABLES
- Every screen above as high-fidelity frames, arranged in user-flow order with connecting arrows
- A component library: buttons, pills, cards, aura card (all 5 levels), limit row, score-change row, toggles, segmented control, steppers, bottom nav, charts, widgets
- Design tokens: colours, type scale, spacing, radius and elevation, named so they map directly to a Jetpack Compose theme
- A prototype flow: Onboarding → Home → Focus → Session complete → Home with the updated score