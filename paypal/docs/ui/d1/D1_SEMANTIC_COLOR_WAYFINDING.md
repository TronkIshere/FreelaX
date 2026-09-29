# D1.3 — Semantic Color Wayfinding

Status: **provisional desktop exploration**. These are not final global brand tokens. D4 owns the final palette lock.

## Provisional map

| Color | Exploration value | Meaning in Set A |
| --- | --- | --- |
| Bone | `#F2EDDF` | Main canvas, neutral rows, document-like reading areas |
| Ink | `#161915` | Primary text, structural rules, boundaries, high-contrast CTA |
| Vermilion | `#EF5B3E` | Highest visual attention and selective focus; an urgent owner action only when state justifies it |
| Acid Yellow | `#EFD94F` | Current or pending marker, selected row rail, temporary highlight |
| Sage | `#8FA47D` | Owner context, supportive/resolved state, search/filter functional zone |
| Cobalt | `#334EB5` | Finance, system, and evidence domain; reserved and scarcely used in marketplace Set A |

Tinted Sage zones in the prototypes are a local mix of Sage and Bone, not a new locked token.

## Section zoning

- **Search/filter** gets a Sage-tinted, Ink-bounded strip. Fields stay square and inline. The strip reads as a tool, separate from job data.
- **Marketplace rows** stay Bone with Ink rules. A focused row gains an Acid rail and, where useful, one small Vermilion anchor. Normal rows do not get independent identity colors.
- **Applications** use explicit state text plus a rail/shape. Pending uses Acid; accepted uses restrained Sage and a clear transition to assigned work; rejected and cancelled remain neutral and lower contrast without disappearing.
- **Client-owned context** uses a Sage ownership strip with “Bạn là người đăng”. It does not imply payment completion.
- **Attention-required work** may use Vermilion for `SUBMITTED_FOR_REVIEW`, with clear “Client cần xem” copy. `REVISION_REQUESTED` points toward the freelancer's next action; it is not styled as a client emergency.
- **Primary actions** use Ink fill and Bone text. Color anchors identify the section; the button label identifies the action.

## Attention hierarchy

1. **Act now:** bounded Vermilion marker, explicit action-owner copy, strong Ink CTA.
2. **Current / waiting:** Acid rail or strip, explicit waiting label, no fabricated action.
3. **Owned / supportive / resolved:** Sage context, text state, normal hierarchy.
4. **Historical / closed:** Bone and Ink with reduced visual weight, fully readable text.

Color is never the sole signal. Every state also has an explicit label and at least one structural cue: rail, position, ownership sentence, or action label. Focus states use a visible Ink/Vermilion outline; they cannot depend on hover color. Text remains Ink on light surfaces for contrast. The prototypes use no animation to communicate state.

## Examples and anti-patterns

| Use | Avoid |
| --- | --- |
| Bone row + Acid left rail + “Đang chờ Client” | A random yellow job with no status text |
| Vermilion review marker + “Client cần xem” + “Mở công việc” | Every job using a red fill, or red used for rejected applications |
| Sage owner zone + “Bạn là người đăng” | Sage as an arbitrary brand color on one client's jobs |
| Ink “Ứng tuyển” action on an eligible OPEN job | Color-only apply eligibility or a false success state |
| Cobalt reserved for later finance/evidence screens | Blue marketplace rows or a dominant Web3 palette |

The static studies illustrate hierarchy, not live eligibility. An implementation must recheck the current server state before applying or showing owner actions. No payment or budget edit behavior is introduced here.
