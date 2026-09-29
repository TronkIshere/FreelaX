# D1.5 — Marketplace Design Decision

Decision: **APPROVED**

Approved grammar: **FreelaX Editorial Dense Marketplace**

Scope: D1 desktop marketplace surfaces. Human visual review accepted the D1.3a color-calibrated direction; no further marketplace redesign is requested before D1 closure. This decision does not authorize source implementation.

## Inputs and synthesis

Study C, Dense Marketplace List, supplies the structural backbone: ruled dense rows, stable comparison columns, and capacity for long job lists. Study A, Editorial Grid, supplies decisive headings, deliberate line breaks, spacing rhythm, and low dependence on containers. Study B, Graphic Split, supplies selective saturated blocks, state rails, graphic anchors, and controlled asymmetry. D1.3 combined these as one screen grammar; D1.3a calibrated its color energy without changing the work, fields, states, or actions.

The canonical scan order is **WORK → STATE → OWNERSHIP → NEXT ACTION**. Budget, date, and description support that order. A job remains the organizing object. The design is work-first, not wallet-first, token-first, fintech-first, or dashboard-first. Avoid the generic card → metadata → status pill → button sequence.

## D1 approved marketplace study values

| Color | Study value | Marketplace role |
| --- | --- | --- |
| Cream | `#FFF7E8` | Dominant editorial canvas and neutral work rows |
| Ink | `#17212B` | Type, rules, dividers, boundaries, and primary CTA |
| Vermilion | `#F15A3D` | Bounded high-priority attention and selective graphic focus |
| Acid Yellow | `#F5D12F` | Current or waiting state rail and focused item marker |
| Fresh Mint | `#B8DFC4` | Functional tool zone, ownership, accepted or resolved context |
| Cobalt | `#3567E8` | Reserved primarily for later finance, evidence, and system surfaces |

These are **D1 approved marketplace study values**, not final global brand tokens. D4 owns the shared token and palette lock. Color must accompany explicit state text and a structural cue; normal rows stay calm and Cream remains dominant. The former muted/olive Sage marketplace treatment is superseded by Fresh Mint.

## Approved desktop surfaces

- Freelancer Discover: scan OPEN jobs, focus one record, understand eligibility context, then apply after a live server recheck.
- Freelancer My Applications: distinguish PENDING, ACCEPTED, REJECTED, and CANCELLED without color-only or invented actions.
- Client My Jobs: prioritize SUBMITTED_FOR_REVIEW, then distinguish waiting, active, resolved, and historical jobs.
- Client Job Detail: show the Client owner, OPEN state, read-only budget, and application review as the primary next action.

The four surfaces pass the read-only [final visual QA](D1_FINAL_VISUAL_QA.md). Static controls are design examples, not working API integrations.

## Extension rules and boundaries

1. Reuse the work/state/owner/action order, ruled structure, explicit status language, and bounded emphasis. Adapt density and layout to each later screen's task; do not copy these four layouts wholesale.
2. Give each new state a written meaning, responsible role, structural marker, and permitted action before assigning a color. Verify live role, ownership, and state before offering an action.
3. Keep money attached to a job. Do not equate work completion with payment, payout, or tax completion. Budget editing remains disabled until the checkout amount contract is resolved.
4. Reject generic SaaS dashboards, bento grids, glass/blur/glow, wallet-first navigation, cards for every object, dark Web3 styling, random row colors, and poster overload.

Mobile is deferred by team priority. Final global design tokens remain with D4; D2 owns the work-state workflow, D3 owns financial surfaces, and logo asset integration remains a separate workpack. The selected FX Cut Mark in Ink + Vermilion is visually compatible with this grammar, but no logo asset is integrated here. Flutter implementation remains unauthorized.
