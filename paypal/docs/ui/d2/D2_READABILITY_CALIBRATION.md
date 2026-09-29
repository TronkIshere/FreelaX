# D2.3 — Readability Calibration

Status: **applied to six separate desktop studies; human full-state visual review pending**. D2.2 correctly communicated the workflow, but its stacked studies placed rails, rules, labels, metadata, actions, and history at competing weights. Stacking three states on each study page amplified that density. D2.3 shows one state per file so the viewport resembles one real work screen.

| Concern | D2.3 rule | Inspection cue |
| --- | --- | --- |
| Hard rules | Reserve strong Ink rules for the major screen edge, workflow rail, document edge, and history separation. Group small facts with spacing. | The current work reads before divider lines. There are approximately 30–40% fewer visually dominant rules than the D2.2 studies as a design target, not a pixel metric. |
| Typography | Use a large, sentence-case current-work heading, readable summary/feedback and short line lengths. Reserve uppercase for compact state/role and metadata keys. | Submission prose reads as content, not a tiny technical label. |
| Focus | Current work first, primary action second, owner/state third, workflow fourth, history last. | One dominant object and one clear action area per active screen. |
| Semantic zones | Cream is the neutral canvas. One bounded state/owner zone uses Mint, Vermilion, Acid, or resolved Mint. Ink carries the primary action. | No rainbow sections; color never replaces text. |
| Rail | Seven chronological words remain; current step has a clear marker, past steps recede, future steps recede further. | Rail answers position without winning the page. |
| History | A compact ascending ledger follows current work. Show status, time, and one-line continuity, not a duplicate full submission. | V1 remains traceable after revision and V2; V2 is not shown as persisted before submit. |
| Ownership | A compact band below the rail states who acts and who waits. No permanent ownership sidebar. | The current actor is identifiable without scanning the history. |
| Metadata | Group budget, URL, and timestamps next to their related work object. Keep job budget read-only. | Technical details do not compete with summary or feedback. |

## State application

- `IN_PROGRESS`: mostly Cream; a light Fresh Mint ownership cue and Ink action support focused work.
- `SUBMITTED_FOR_REVIEW`: Cream keeps the latest submission legible; Vermilion marks Client attention around the decision/owner area, not the whole page.
- `REVISION_REQUESTED`: an Acid feedback surface leads; the V2 composer remains neutral; Ink carries “Gửi bản sửa”.
- `COMPLETED`: Fresh Mint provides quiet closure; no work CTA, celebration, or financial claim.

## Accessibility and reduced motion

Text labels, state names, owner sentences, and chronology remain readable without color. Use semantic headings, a labelled workflow navigation region, explicit form labels, logical reading order, visible keyboard focus, and Ink/Cream text contrast. Buttons are static design controls, not live API actions. At 1440px and 1280px, content stays within the desktop canvas without forcing metadata into the main reading line. If animation is later implemented, only a server-confirmed handoff may use opacity plus at most 8px translation for 140–180ms; reduced motion keeps the same order and focus with an instant update or opacity only.

No global palette lock or Flutter implementation is authorized by this calibration.
