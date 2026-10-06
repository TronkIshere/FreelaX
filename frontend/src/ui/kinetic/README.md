# FreelaX kinetic primitives

Presentation only. Import from `./ui/kinetic`; the barrel includes scoped
`ku-*` CSS. The existing shared headings, facts, disclosures, buttons and
page layouts remain authoritative. The App navigation and both role-aware
Overview variants now consume these primitives in the actual application.

## Composition

| Primitive | Purpose |
| --- | --- |
| `KineticCard` | One document/action surface: square 2px Ink border, 6px hard shadow; five locked surface variants. `as="section"`, `div` for a fact group inside a `dl`, or default `article`. `depth="compact"` uses 3px → 4px hard depth. |
| `KineticLedgerRow` | A flat `li` for an existing list. Caller supplies title, thumbnail, metadata, status, value and action. No default state, amount or control. |
| `KineticThumbnail` | Abstract identity decoration. Supply the actual record ID as `identity`; no job imagery, state inference or fake screenshot. |
| `RoughArrow` | Seeded curved/straight decorative pointer, right/up-right/down-right. |
| `RoughBurst` | Seeded three rays or eight-ray emphasis. |
| `RoughUnderline` | Seeded single/double stroke; optional controlled `active` gives a short sweep. |
| `CutPaperShape` | Rectangle, notch, fold or strip; small/medium/large, controlled rotation. Place/layer with page CSS. |
| `TapeSticker` | Caller-supplied label, slight static rotation plus 1° micro tilt. Acid, Cream, Cobalt or Vermilion. |
| `KineticLabel` | A compact square category surface without tape rotation. Caller supplies text. |
| `KineticActionArrow` | Functional Lucide direction; put inside a link/button with a real accessible label. |
| `PrintTexture` | Optional empty halftone overlay for decorative paper/thumbnail surfaces only. |

### Overview Vermilion action region

Use `KineticCard variant="vermilion"`, `TapeSticker`, 2–3 `CutPaperShape`
layers, `RoughBurst` near the existing visual and `RoughArrow`/`RoughUnderline`
near the existing action/value. Keep actual job content and CTA from the page.
Cream foreground is the default on Vermilion/Cobalt; existing `.button`
remains Ink/Cream. Do not turn every section into a card or sticker.

### Overview Recent Work

Use `KineticLedgerRow` inside a `ul`, `KineticThumbnail identity={job.id}`,
the existing title/status/amount formatting, and an existing router `Link`
in the action slot. Supply attention only from verified page logic. Add a
`KineticActionArrow` inside that link. Nothing in the toolkit fetches data,
selects destinations or infers permissions.

## Determinism and semantics

`seedKey` hashes to a positive 31-bit Rough.js seed. Stable default keys are
available for repeated identical marks. Supply a record/context key for
variation. Re-rendering and remounting with the same key gives the same paths;
thumbnail identity survives list reordering. No clock or `Math.random` input.

Paper, texture, thumbnail and Rough marks are decorative and hidden from the
accessibility tree. Tape and category labels preserve caller-supplied text.
Ledger links/buttons retain their normal keyboard semantics; document surfaces
do not create extra tab stops. Functional icons use named `lucide-react`
imports (for example Home, BriefcaseBusiness, ChartNoAxesColumnIncreasing,
Bell, UserRound). App navigation and Overview metrics use named Lucide icons.
Rough marks and geometric paper carry the decorative personality.

## Motion

`kineticMotion`, `kineticVariants` and `useKineticMotion` are reusable with
`motion/react`. All presets use a 160ms tween with an ease-out curve:

- Card: −1px x/y; hard shadow 6px → 8px.
- Compact facts: −1px x/y; hard shadow 3px → 4px.
- Ledger: +3px x; thumbnail +1°/−1px; functional arrow +4px.
- Tape inner label: +1° relative to its fixed paper angle.
- Underline: 65% → 100% sweep, controlled by `active`; full stroke by default.

Card and ledger propagate `rest`/`active` variants on hover or descendant
keyboard focus. `KineticActionArrow` inherits those variants, or accepts an
explicit `active` for a page-owned CTA. A normal button can use the same
presets on a `motion.button`; keep the label and click handler on the button.
No entry animation hides content; no loops, springs, bounce or parallax.

Every animated primitive accepts `motion={false}`. Card/ledger opt-out also
disables descendant toolkit motion. The preference subscription responds to
OS changes while mounted and is cleaned up on unmount. Unknown/SSR preference
defaults to static. A CSS reduced-motion rule also suppresses transforms and
retains the original hard shadow. Static paper placement/rotation remains.

## Styling boundaries

Only the six locked palette colors; no arbitrary drawing/styling API.
The CSS is local to `ku-*`, reuses existing global tokens, and provides palette
fallbacks for isolated tests. Card corners are square; shadows have zero blur.
Ledger rows have rules and no elevation. Texture is opt-in, 7% opacity, hard
halftone dots: never place it over text, inputs, long content or financial
values. Decoration and page spacing belong to page composition, not these
primitives. No dashboard grid, generic metrics wall or product state machine.

## Validation surface

`kinetic.test.tsx` uses the existing Vitest/jsdom setup; no Storybook, new app,
route, stock imagery or fixture-backed product screen. It tests deterministic
SVGs, all surface/thumbnail variants, supplied content, list/keyboard semantics,
opt-out inheritance, preference changes and subscription cleanup.
