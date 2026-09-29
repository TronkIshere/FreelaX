# D2.4 — Kinetic Workflow Design Decision

Status: **APPROVED** for the D2 desktop work-state design set. Canonical grammar: **FreelaX Kinetic Document Workflow**. The final six product-state screens are the `d2_3_01` through `d2_3_06` HTML files listed in [D2_FINAL_VISUAL_QA.md](D2_FINAL_VISUAL_QA.md). D2.1 storyboard and D2.2 A/B/C studies remain decision evidence, not the final product-screen grammar. Human-directed progression through D2.3b and the final source/contract QA found no defect requiring a prototype change. Rendered screenshot QA was unavailable in this environment; that limit is recorded in the QA document.

## Lineage and synthesis

| Stage | Contribution |
| --- | --- |
| D2.1 | Locked backend contract, deterministic job and submission fixture, six-frame state story, action ownership, and motion semantics. Human review: PASS. |
| D2.2 | Three distinct workspace/review studies: A rail first, B document first, C handoff/version ledger. Human review: PASS. |
| D2.3 | Human-selected hybrid and six separate desktop product-state screens. |
| D2.3a | Filled, bordered current rail cell and compact semantic section signposts. |
| D2.3b | Reduced current-work type scale and clearer revision ownership/feedback contrast. |
| D2.4 | Final six-screen source and contract QA, design lock, and merge preparation. |

**Study B** supplies the document-first backbone. **Study A** supplies the compact seven-step rail and adjacent ownership band. **Study C** supplies the compact, ascending version ledger. FROM → TO is transition feedback after a confirmed handoff, not permanent page chrome. One HTML file represents one current state.

## Canonical hierarchy and states

The visual priority is **current work → current action → owner / state → workflow position → history / system notes**. The job title provides identity; the latest brief, submission, feedback, or approved record is the principal reading object. An Ink primary CTA belongs to the verified actor. The rail names Assigned, Working, Submitted, Review, Revision, Resubmitted, Approved as visual chronology, not as seven backend `JobStatus` values.

| Job state in the six-screen set | Owner and work object | Semantic treatment |
| --- | --- | --- |
| `IN_PROGRESS` | Freelancer owns the brief or prospective V1 composer; Client waits. | Cream with restrained Fresh Mint support; Working is the filled Mint rail cell. |
| `SUBMITTED_FOR_REVIEW` | Client reviews latest V1 or V2; approval is primary and revision is secondary. | Cream with bounded Vermilion attention; latest submission stays above quieter history. |
| `REVISION_REQUESTED` | Freelancer sees exact Client feedback, then a prospective V2 composer. | Full Acid Revision cell; Acid feedback label and 26% tinted body with Ink edge; ownership remains Cream with a narrow Acid top strip. |
| `COMPLETED` | No work-action owner or work CTA; approved V2 is the current record. | Cream with restrained Fresh Mint closure; V1 and V2 remain in the ledger. |

Submission accepts only required `summary` and optional `deliverableUrl`. V1 survives revision; V2 is a new persisted record only after submission. Approval has no request body. Completion of work does not claim payout, off-ramp, or tax completion. The exact six-screen contract is in [D2_FULL_STATE_APPLICATION.md](D2_FULL_STATE_APPLICATION.md).

## Motion, signposting, and extension

Motion remains a specification: confirm the server state first, retire the old owner/action, update the rail and next owner, preserve or insert the version record, then transfer focus. A short opacity change with at most 8px translation over 140–180ms may explain the handoff. Reduced motion disables translation and stagger, using an instant state change or opacity alone. The static hierarchy must carry every meaning.

Compact category cues distinguish current work (Ink), latest submission (bounded Vermilion), Client feedback (Acid), submission history (Fresh Mint), and approved record (Fresh Mint). The current rail cell also has a written label, position, fill, and Ink border; color alone never defines state. History remains visible and secondary. Later work states must establish their real backend state, actor, current object, and permitted action before extending this grammar.

Rejected patterns: bento and KPI dashboards, floating card systems, glass/blur/glow, soft-shadow SaaS framing, status pills as primary language, kanban, wallet-first or dark Web3 styling, decorative motion, upload/attachments, chat, invented milestones, manual payment controls, and invented approval fields. The study palette is approved for D1/D2 use only; **global tokens are NOT LOCKED**.

## Scope boundary

Financial surfaces belong to **D3**. Global palette and design tokens belong to **D4**. Mobile remains **DEFERRED**. Flutter implementation, including `paypal/lib/**`, is **PERMANENTLY OUT OF SCOPE for this design lane**. Backend is a read-only contract reference; payment and backend source are not modified by this decision. D2 lock does not authorize a master merge, PR, or source implementation.
