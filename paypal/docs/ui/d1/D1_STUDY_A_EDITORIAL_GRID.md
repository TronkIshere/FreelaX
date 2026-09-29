# D1.2 A — Editorial Grid

Uses [the locked D1 fixture](D1_FIXTURE.md) without changing its four jobs, role context, actions, or temporary palette. This is one composition study, not a selected design.

1. **Design hypothesis:** A useful marketplace can gain a distinct voice through a clear page rhythm, generous negative space, and a decisive job title. The job remains the object of attention.
2. **Composition grammar:** An asymmetric desktop grid gives the focus job a large type area and a narrow factual margin. Secondary jobs run as open rows separated by ink rules. A quiet masthead identifies **Công việc / Khám phá**, not a marketing hero.
3. **Information hierarchy:** Screen identity → focus title → client display name / `OPEN` / budget → description → **Chưa ứng tuyển** → **Ứng tuyển**. The three other jobs follow in the same schema and order. Search and the four supported filter dimensions stay above the results.
4. **Typography behavior:** System sans, with one large display step reserved for the focus title. Metadata uses smaller, firm labels and tabular amounts. Line breaks are editorial but must remain readable at 320px. No downloaded or final font selection.
5. **Shape language:** Minimal containers; 1px to 2px ink dividers and a narrow vermilion marker. No repeated cards, soft corner system, or nested frames.
6. **Color-block behavior:** Bone is the reading field, Ink carries information, Vermilion marks the active focus/action, Acid Yellow is a small orientation accent, Sage is a restrained secondary accent. These are the shared temporary D1 colors, not brand tokens.
7. **CTA treatment:** The focus **Ứng tuyển** is a solid ink rectangle with a hard boundary. Secondary row actions are consistent text-and-rule affordances, not oversized banners. Client proof keeps **Xem ứng tuyển** primary and title/description edit secondary.
8. **State treatment at D1.2 scope:** `OPEN` and **Chưa ứng tuyển** are written plainly; a short rule anchors status near the title. Color is supplementary. Other states are deferred to D1.3.
9. **Desktop behavior:** Large title and fact column sit side by side; rows align client, job, budget, and action to a common baseline. The layout remains a task page, with filters and result count in normal reading order.
10. **Mobile behavior:** At 390px and 320px, the fact column folds below the title; rows become stacked lines with the action following context. Inputs wrap and all text can break without horizontal scroll. This is a D1.2 preview; D1.4 will test the broader device matrix.
11. **Intentionally avoids:** Magazine-only storytelling, decorative editorial copy, wallet motifs, rating/count badges, a hero CTA that hides jobs, and budget editing.
12. **Strengths:** Clear reading order; strong identity with little visual furniture; naturally translates from list to detail.
13. **Risks:** One oversized focus title can consume too much of a small viewport. Secondary rows need a robust dense variant when there are 20–30 jobs.
14. **D1.3 tests:** Applied vs not-applied states, stale `OPEN` result handling, long client names, long descriptions, pagination and empty/error states, focus/keyboard treatment, and whether hierarchy survives 20–30 rows.
15. **Prototype path:** `paypal/docs/ui/d1/prototypes/study_a_editorial_grid.html`.
