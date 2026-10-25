# Orbit design system

**Direction.** Orbit taken literally: a calm, deep space where the people you should call float around
you. Premium, minimal, a little playful. The person you most likely want to call is a glowing orb at the
top and center of the field; the others sit in tidy rows below and beside it, smaller and dimmer the
lower their priority.
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

**Never color alone.** Priority is size, glow and position in the rows. A missed call is a badge icon,
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

The field (`OrbitLayout`, pure) has three layouts, chosen in Settings with a small preview of each:

- **Grid 3 (default):** rows of 3 equal bubbles from the left, #1 top-left, left to right and top to bottom, sized to the screen width.
- **Grid 4:** the same with 4 per row.
- **Spotlight:** the first row has #1 largest in the centre with #2 and #3 beside it, then rows of 4 smaller bubbles.

In every layout each bubble has only its name under it, the list scrolls and loads more rows (capped), nothing sits on a ring
(a faint halo only in Spotlight), and slot numbers are rank positions. Rank changes animate bubbles to their new places; the
bubbles are otherwise still, and the glow behind #1 breathes. A pinned bubble shows a small pin marker. Tap opens the quick actions; press and hold lifts a bubble (scale and a brighter glow): drag
it and the others slide aside with the rank-change animation, a ring with the slot number shows where it will land (in Spotlight it
takes the size of that slot), and the field scrolls near the top and bottom edges. A pinned slot is not a valid target: no ring,
a small not-allowed cue and a soft haptic, and a drop there glides back. Releasing a hold without moving pins or unpins at the slot.
Confirmations (pin, unpin, move) use one small glass toast with Undo. Reduced motion snaps everything.

Controls are minimal. **Header:** the small gradient "Orbit" wordmark and AI sparkle status on the left; Rules and add general instruction
(sparkle) on the right. **Bottom bar:** a round glass Settings button on the left, the "Open dialer" pill (accent gradient in
dark, deep navy in light) in the middle, a round glass Search button on the right, above the nav bar. Swiping down on the
bubbles refreshes with the sparkle indicator. **Reason card** (one
place for the reason, above the bar): "Why Gil is first" for the top contact; while a bubble's quick actions are open
it swaps (fade and slide) to that contact's "Why Mom is #6" and reason, and returns to the top contact on close. It is
hidden while search is open.

**Search:** tapping the button slides a field down at the top, under the status bar (spring and fade, focus and keyboard at
once; it never sits on the keyboard) and the bottom bar fades out. The bubbles dim and blur, and a scrollable glass panel under
the field, sized to the space above the keyboard, shows, when the field is empty,
up to 8 contacts as "Frequent" (most calls in 90 days) and "Recent" (latest calls), then matches while typing (all
contacts, hidden ones labelled "Hidden from suggestions"). Results open the same actions as a bottom sheet. Closed by
back, the close icon or a tap outside; reduced motion snaps.

**AI sparkle convention:** one icon (`OrbitIcons.Sparkle`, a large four-point star with a small one) means "AI" everywhere:
the "Note"/instruction actions and the header status. Status: shimmering while a real refresh runs (still with reduced
motion), dimmed with a slash when AI is off or the last refresh failed (tap opens Settings), absent otherwise.

## Action colors (tokens `Orbit.colors.action*`)

| Action | Gradient (top to bottom) | Glyph |
|---|---|---|
| Call | `#1E88E5` to `#1565C0` | white phone |
| WhatsApp | `#1DA851` to `#148A41` (WhatsApp green, darkened to reach 3:1 with the white glyph) | white WhatsApp glyph (`ic_whatsapp`) |
| Contact card | `#8E5CF0` to `#6D3FD6` | white person |
| Details | `#E06A22` to `#C4501A` | white info |
| Note (AI) | `#7C5CFF` to `#0E8FC2` | white sparkle |

Green is used only for WhatsApp. Every glyph is at least 3:1 on its circle, and each action also has its own icon and label.

## Quick actions

Tapping a bubble lifts it forward (scale and glow), softly blurs (Android 12+) and darkens the rest, and
springs the actions out of the bubble onto an arc, one after another. `QuickLayout` (pure, unit-tested)
chooses where the arc goes: it prefers above the bubble, then turns to the side or below, and rejects any
position where a button or its label would leave the screen, cover the bubble's name or touch the bubble.
Labels sit on the outer side of each button; on large fonts only the pressed button shows its label. The
"why this contact" note is a separate caption pill. "Note" collapses the arc into a floating glass field
with a send button. A haptic tick on open and on choosing; dismiss by tapping outside or back. The actions
are a plain list (`quickActionsFor`), so adding a button needs no layout change.

## Settings

A gear button next to Rules opens Settings: the AI switch, last update and status, "Refresh now", and "What the LLM sees" (request, raw answer, validation issues, monospace and selectable). A small cloud-off icon appears next to the title when the last refresh failed; the last good suggestions stay on screen.

## Rules and Details

Same language: ink background, glass cards, Outfit. The two rule families look different on purpose:
free-text rules (read by the LLM) are soft and fluid, with round gradient violet-to-aqua cards; typed rules
(enforced by code) are crisp and solid, with square corners, an opaque fill and a hard teal edge. Sections
have a one-line subtitle and an info icon that opens the longer explanation. Custom chips and an inline add
field with a send button replace the stock ones; empty states are short and a little playful.

## Motion

Tokens in `Motion`: 120 / 220 / 340 ms; entering things decelerate, leaving things accelerate and are
quicker; springs for the arc and the lift. Bubbles fade and scale in once, staggered. The top glow breathing (5s) is the only ambient motion; bubbles do not drift. **Reduced motion:** when system animations are
off, every spec snaps and the breathing and pulse are not run.

## Planned UX tweaks

- Clean interface preferred. **Done:** the missed-call badge icon is removed from bubbles; a missed call is no longer marked on the bubbles at all; the red "Missed today" / "Missed 2d" text shows only in the quick-actions popup, and the spoken description still says it.

## Notes

- Dark vs light follows the system setting; dark is the designed-first experience.
- Background blur needs Android 12 (API 31); on older versions the screen only darkens.
- Typed rules' "crisp" look is deliberately the most "enforced"-feeling thing in the app.
