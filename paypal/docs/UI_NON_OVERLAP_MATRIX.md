# FreelaX UI Ownership / Non-Overlap Matrix
Status: CONTROL DOCUMENT

| File / Area | Owns | Must NOT Own |
|---|---|---|
| PRODUCT_UI_MEMORY.md | product framing, role principles, canonical IA | detailed component specs, mockups, CSS tokens |
| SCREEN_ARCHITECTURE.md | screen inventory, role/state/action behavior, backend readiness | visual styling, palette, art direction |
| ART_DIRECTION_FREELAX.md | movement, aesthetic rules, composition, kinetic principles | product flows, endpoint contracts, permissions |
| DESIGN_SET_PLAN.md | set ownership, sequencing, deliverables, approval gates | final tokens, backend behavior |
| UI_DESIGN_MASTER_PLAN.md | roadmap, dependencies, gates, acceptance criteria | live progress state, product semantics |
| UI_DESIGN_PROGRESS.md | live task/phase status and checkpoint log | detailed specs or new rules |
| Workpack files | exact implementation scope | unapproved product/design changes |
| Backend contract docs | API/state contract facts | UI art direction |

## Decision precedence
1. Backend/source contract facts
2. Product UI Memory
3. Screen Architecture
4. Art Direction
5. Approved Design Set output
6. Implementation workpack
7. Code

A lower layer may not silently override a higher layer.

## Set ownership
| Set | Primary ownership | Explicit exclusions |
|---|---|---|
| A Marketplace Editorial | discover, applications, job lists/details | finance, auth, shell |
| B Kinetic Workflow | workspace, submit, review, revision/approval | discovery, tax, auth |
| C Financial Evidence | payment, payout, tax, certificate | marketplace discovery, auth |
| D Shell / Activity | role-aware nav, overview, activity | detailed work/financial internals |
| E Auth / Identity | login, registration, account identity | navigation IA, backend auth contract |

If one set needs an element owned by another, use a placeholder/reference. Do not redesign it.

## Shared-components policy
Only after Sets A–C expose recurring patterns may we extract typography, spacing, borders, radii, shadows, actions, fields, state markers, rails, evidence rows, event items and motion tokens.

Do NOT create a universal Card component or universal status-pill grammar.

## Implementation boundary
Until an implementation workpack explicitly says IMPLEMENTATION_AUTHORIZED: YES:
- no Flutter production changes
- no backend changes
- no dependency changes
- no navigation/API changes
- no opportunistic stale-screen cleanup
- no repo-wide refactor
