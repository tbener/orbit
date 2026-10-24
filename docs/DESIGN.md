# Orbit design system

**Direction.** Orbit taken literally: a calm, deep space where the people you should call float around
you. Premium, minimal, a little playful. The person you most likely want to call is a glowing orb in the
middle; the others drift on faint orbit rings around it, smaller and dimmer the further out they are.
Dark is the primary design (deep ink with a hint of navy, bright glossy orbs, one aqua-to-azure accent);
light is its own design (soft off-white with pastel light and pastel orbs), not an inversion. Nothing is
stock Material: surfaces are frosted glass, type is Outfit, shapes are custom.

Everything is implemented as tokens in `ui/theme`. Screens use `MaterialTheme.colorScheme`,
`MaterialTheme.typography`, `MaterialTheme.shapes` and `Orbit.*`; they do not hard-code colors, sizes or
durations. Screenshots of both themes are in `docs/design/screenshots/`.

## Color

| Role | Dark | Light |
|---|---|---|
| background gradient | `#090E1F` to `#0E1730`, faint stars | `#F7F8FF` to `#ECF1FB`, pastel blobs (lilac, peach, mint) |
| text | `#EEF2FF` | `#10172E` |
| secondary text | `#A9B4D0` | `#4A5578` |
| signature accent | aqua `#5EEAD4` to azure `#4C8DFF` | teal `#14B8A6` to blue `#3B6EF5`; solid `#0F766E` |
| alert (missed call) | ember `#FF7A59`, ink icon | `#C9361F`, white icon |
| free-text rules ("soft") | violet `#B5A6FF` | `#6D5BD6` |
| typed rules ("solid") | teal `#4FE0CF` on `#0F2230` | `#0F766E` on `#E2F3F0` |
| glass fill / border | white 7-12% / white 30% to 6% | white 66-82% / white to `#C9D2EA` |

**People palette** (`Orbit.colors.bubbleTones`): Aqua, Sky, Violet, Rose, Amber, Silver. Each orb is a
gradient from a lighter top to a deeper bottom. Dark orbs are bright with ink initials (at least 5.6:1);
light orbs are pastel with deep initials (at least 5.3:1). A contact keeps the same tone for good;
rank never changes the hue.

**Never color alone.** Priority is size, glow and position on the rings. A missed call is a badge icon,
the word "Missed" and the spoken description. A pinned contact has a pin badge and "pinned" in the
spoken description. Soft and solid rules differ in shape, icon and label as well as color.

**Contrast** (checked with a script): all text pairs at least 4.5:1, including the accent, the alert and
text on the orbs.

## Typography

**Outfit** (SIL Open Font License, `docs/licenses/OFL-Outfit.txt`), one variable font file for every
weight: a geometric, slightly rounded sans. Sizes are in sp.

| Style | Size / line | Weight | Used for |
|---|---|---|---|
| headlineSmall | 28 / 34 | Medium | contact name on Details |
| titleLarge | 28 / 34 | Medium | screen titles |
| titleMedium | 17 / 24 | SemiBold | section headers, the top bubble's name |
| titleSmall | 15 / 21 | SemiBold | card titles |
| bodyLarge / bodyMedium / bodySmall | 16 / 14 / 13 | Regular | lists, notes, subtitles |
| labelLarge | 14 / 20 | Medium | bubble names, buttons |
| labelMedium / labelSmall | 12 / 16 | Medium | subtitles, arc labels |

Nothing is below 12sp. At large font scales everything grows, except bubble names and subtitles, which
stop at 150% because they sit under fixed orbs (the full text is in the quick-actions caption and on
Details). The add field stacks above its send button on large fonts.

## Shape, spacing, depth

- **Shapes:** `MaterialTheme.shapes` 6 / 10 / 16 / 24 / 32dp, plus `PillShape`, `SoftShape` (26dp, free-text
  rules) and `CrispShape` (8dp, typed rules).
- **Spacing:** `Orbit.spacing` 2, 4, 8, 12, 16, 24, 32dp. **Touch targets:** at least 48dp.
- **Glass:** `Modifier.glass(shape)`: translucent fill, a border that is brighter at the top, a soft
  shadow. Anything that has text over moving content has an opaque base under the glass.
- **Depth:** orbs have a soft colored shadow and a glow; the top bubble glows most.

## Home

The field (`OrbitLayout`, pure and unit-tested) puts the top bubble in the middle and the rest on
concentric elliptical rings, each ring a little smaller. A bubble is placed only where it and its name
stay on screen and clear of the others; when a ring is full the rest move outward, so the field scrolls
and loads more rings near its end. The bubbles drift very slowly and the glow breathes.

Controls are minimal: a slim translucent search pill, glass icon buttons (rules, add general
instruction), a small pulsing dot next to the title while refreshing, and a floating "Open dialer" pill
(accent gradient in dark, deep navy in light) with a soft glow.

## Quick actions

Tapping a bubble lifts it forward (scale and glow), softly blurs (Android 12+) and darkens the rest, and
springs the actions out of the bubble onto an arc, one after another. `QuickLayout` (pure, unit-tested)
chooses where the arc goes: it prefers above the bubble, then turns to the side or below, and rejects any
position where a button or its label would leave the screen, cover the bubble's name or touch the bubble.
Labels sit on the outer side of each button; on large fonts only the pressed button shows its label. The
"why this contact" note is a separate caption pill. "Note" collapses the arc into a floating glass field
with a send button. A haptic tick on open and on choosing; dismiss by tapping outside or back. The actions
are a plain list (`quickActionsFor`), so adding a button needs no layout change.

## Rules and Details

Same language: ink background, glass cards, Outfit. The two rule families look different on purpose:
free-text rules (read by the LLM) are soft and fluid, with round gradient violet-to-aqua cards; typed rules
(enforced by code) are crisp and solid, with square corners, an opaque fill and a hard teal edge. Sections
have a one-line subtitle and an info icon that opens the longer explanation. Custom chips and an inline add
field with a send button replace the stock ones; empty states are short and a little playful.

## Motion

Tokens in `Motion`: 120 / 220 / 340 ms; entering things decelerate, leaving things accelerate and are
quicker; springs for the arc and the lift. Bubbles fade and scale in once, staggered. The drift (18s per
cycle) and the glow breathing (5s) are ambient and slow. **Reduced motion:** when system animations are
off, every spec snaps and the drift, breathing and pulse are not run.

## Notes

- Dark vs light follows the system setting; dark is the designed-first experience.
- Background blur needs Android 12 (API 31); on older versions the screen only darkens.
- Typed rules' "crisp" look is deliberately the most "enforced"-feeling thing in the app.
