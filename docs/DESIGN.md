# Orbit design system

**Direction.** Orbit should feel like a calm ring of people around you, not a spreadsheet. Warm paper-like
surfaces, soft round shapes and a little depth: the person you most likely want to call sits big and
nearest the middle, the rest drift outward and get smaller. One indigo accent, a teal for the hard
rules that are enforced, and a coral for things that need you (a missed call). Motion is quiet and only
explains what changed.

Everything is implemented as tokens in `ui/theme`. Screens use `MaterialTheme.colorScheme`,
`MaterialTheme.typography`, `MaterialTheme.shapes` and `Orbit.*`; they do not hard-code colors, sizes or
durations.

## Color

Warm neutrals with an indigo accent. Light and dark are separate, hand-tuned palettes (not inversions).

| Role | Light | Dark | Use |
|---|---|---|---|
| background / surface | `#FBF8F4` | `#14120F` | screens |
| onBackground | `#1F1B16` | `#EAE2D8` | primary text |
| onSurfaceVariant | `#52473E` | `#CFC6BA` | secondary text, subtitles |
| primary | `#4650C8` | `#BFC5FF` | accent, selection ring, links |
| primaryContainer | `#E1E4FB` | `#32399A` | "why" note, general-instruction button |
| tertiary / container | `#1F7A6D` / `#D3EDE7` | `#8FD6CB` / `#0F4F46` | typed (enforced) rules |
| error | `#BA3A3A` | `#FFB4AB` | missed-call badge, destructive actions |
| alertText (`Orbit.colors`) | `#8F2A2A` | `#FFB4AB` | small alert text such as "Missed" |
| surfaceContainer… | `#F1EBE4`… | `#211E1A`… | search field, cards, buttons |

**Bubble tones** (`Orbit.colors.bubbleTones`): Indigo, Rose, Sage, Amber, Sky, Lilac, Stone. Each has a
soft fill, a strong fill and an initials color, with at least 5.4:1 contrast in every case. A contact
keeps the same tone forever. The top two tiers use the strong fill so they stand out slightly; tone never
encodes priority.

**Never color alone.** Priority is shown by size and depth. A missed call is shown by a badge icon, the
word "Missed" and the spoken description. A pinned contact has a pin icon and "pinned" in the spoken
description. Free-text and typed rules differ by label ("Read by the LLM" / "Enforced by code"), icon and
tint. The selected bubble has a ring and a spoken "Selected".

**Contrast** (checked with a script): all text pairs at least 4.5:1, outline against background at least
3:1 in dark (3.96:1 in light).

## Typography

System sans-serif (always available, follows the user's font settings), sizes in sp.

| Style | Size / line | Weight | Used for |
|---|---|---|---|
| headlineSmall | 26 / 32 | SemiBold | contact name on Details |
| titleLarge | 24 / 30 | SemiBold | screen titles |
| titleMedium | 16 / 22 | SemiBold | section headers, card titles, top bubble name |
| titleSmall | 14 / 20 | SemiBold | dialog sub-headings |
| bodyLarge | 16 / 24 | Regular | lists |
| bodyMedium | 14 / 21 | Regular | notes, rule text |
| bodySmall | 12 / 17 | Regular | helper text |
| labelLarge | 14 / 20 | Medium | bubble names, buttons |
| labelMedium / labelSmall | 12 / 16 | Medium | subtitles, tags, quick-action labels |

Nothing is below 12sp. At large font scales (130%, 200%):

- Everything grows with the user's setting, with one deliberate exception: bubble names and subtitles stop
  growing at 150%, because they sit under fixed-size circles. The full text is always available at any
  size in the quick-actions card and on the Details screen. Above 130% bubble names may wrap to two lines
  and labels get 16dp more width.
- Section headers drop the tag chip under the title, and the add-instruction field stacks above its
  button, instead of squeezing text into a few characters per line.
- The search placeholder stays on one line and ellipsizes.

## Shape, spacing, elevation

- **Shapes:** 6 / 10 / 16 / 24 / 32dp (`MaterialTheme.shapes`), plus `PillShape`. Cards and the popup use
  24, rows and inputs 16, tags 10, search field and main button are pills, bubbles are circles.
- **Spacing:** `Orbit.spacing` 2, 4, 8, 12, 16, 24, 32dp.
- **Elevation:** `Orbit.elevation` 0, 1, 3, 6, 10dp. Bigger bubbles sit higher (top bubble 6dp, then 3, 2, 1);
  the popup is the highest thing on screen.
- **Touch targets:** at least 48dp (`Orbit.sizing.touchTarget`).

## Motion

| Token | Value | Used for |
|---|---|---|
| short / medium / long | 120 / 200 / 320 ms | |
| enter | decelerate (0.05, 0.7, 0.1, 1) | popup opening, bubbles arriving, a screen coming in |
| exit | accelerate (0.3, 0, 0.8, 0.15), shorter | popup closing, a screen leaving |
| move | standard (0.2, 0, 0, 1) | selection and size changes |

- **Bubbles** fade and scale in once, staggered by 30ms (first 8 only). They do not replay when scrolling
  back or when the list reorders.
- **Popup:** scales from 92% and fades in over 200ms; leaves in 120ms. The scrim fades with it.
- **Refresh indicator:** fades in and out in its reserved space, so nothing shifts.
- **Screens:** a short fade with a small horizontal slide.
- **Reduced motion:** when system animations are off, every spec snaps (`Motion.reduced`).

## Components

- **Bubble:** circle with photo or initials, tier-sized (120 / 80 / 64 / 56dp), shadow by tier, name and
  subtitle below, badge top-right, pin marker bottom-right, ring when selected. Faint orbit rings sit
  behind the top bubble.
- **Quick-actions card:** 304dp wide, 24dp corners, anchored to the bubble (`PopupPlacement`), actions as
  tonal 48dp circles with labels under them.
- **Rule row:** tinted by section (indigo for free-text, teal for typed), leading icon, text, status line,
  edit and delete.
- **Empty states:** an icon, a one-line title and a sentence telling the user what to do next.

## Not done yet

A custom typeface (for example a rounded humanist sans) would add warmth; it needs bundling a font file.
