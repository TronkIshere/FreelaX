# D1.3 — FreelaX Editorial Dense Marketplace

Status: **desktop design study for human review**. Set A remains in progress; this is not a final component system, font lock, or implementation authorization.

## Selection

The human direction combines **C as the structural backbone**, **A for editorial typography and spacing**, and **B for selective graphic emphasis**. C keeps many jobs comparable in one ruled sequence; A lets the work title lead the scan; B makes one focus or action zone recognizable without turning every row into a poster. The same grammar is used for Freelancer Discover, My Applications, Client My Jobs, and Client Job Detail.

## Composition rules

1. Lead with **work → state → ownership → next action**. Budget, date, and description support that order. Keep all values tied to the job or application that owns them.
2. Use a wide Bone reading field, an Ink top rule, square edges, consistent column alignments, and strong row dividers. Rows share the same underlying structure even when one row is focused.
3. Give the page one editorial title and a small context line. Avoid a marketing hero. Reserve the largest type for a focused job title or the page title, never a dashboard metric.
4. Put search and filters in one bounded Sage functional strip. It is part of the document flow, not a floating card. Keep normal rows mostly Bone.
5. Apply a narrow state/focus rail and a bounded graphic block only where the user must look now. Vermilion is an attention anchor; Acid distinguishes a current item or pending state. Do not assign colors to job identities.
6. Make the principal action an Ink rectangle with explicit wording. Secondary actions use an Ink rule and quieter weight. Actions remain state and role guarded in a future implementation; these HTML files make no requests.
7. Keep the wordmark as text. Space in the header can receive the approved FX Cut Mark in a separate workpack; no logo asset is recreated here.

## What was kept and rejected

| Study | Kept | Rejected for D1.3 |
| --- | --- | --- |
| A — Editorial Grid | Decisive titles, whitespace around high-priority work, plain factual margins | Oversized feature treatment on every visit; long lists must remain scannable |
| B — Graphic Split | One strong flat-color anchor for focus and action ownership | Large color slabs on every record; no poster overload |
| C — Dense Marketplace List | Stable columns, ruled rows, high information density, pagination metadata | Uniform Bone from edge to edge; a thin stripe alone cannot distinguish all state priorities |

## Extension rules

- Extend the row grammar by preserving title, explicit state, owner/context, and next-action slots. Adapt row density before introducing a new card type.
- Give a new state a written label, role owner, non-color marker, and action rule before choosing a color. Use the [state matrix](D1_STATE_MATRIX.md) and [semantic wayfinding map](D1_SEMANTIC_COLOR_WAYFINDING.md).
- Treat pagination and status filters honestly: Client My Jobs has no server status filter, so a page-local selection cannot claim to represent all jobs.
- Keep payment, payout, tax evidence, submission review, and the full shell in their owning design sets. A job budget in Client Job Detail is read-only while the checkout amount contract is unresolved.
- These prototypes are desktop studies at 1440px and about 1280px. D1.4 owns responsive behavior; D1.5 owns human screenshot review; D1.6 owns final grammar selection. D4 still owns final token lock.
