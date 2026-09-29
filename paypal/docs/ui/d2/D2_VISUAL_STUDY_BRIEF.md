# D2.2 — Workspace and Submission Review Visual Study Brief

Status: **three desktop studies complete; selection pending human review**. These static compositions explore a layout grammar. They do not implement routes, invoke APIs, finalize D2, or authorize Flutter work.

## D2.1 human review input

D2.1 human visual review: **PASS**. The six frames read as one continuous workflow. The rail communicates Assigned → Working → Submitted → Review → Revision → Resubmitted → Approved without inventing JobStatus values. Ownership reads Freelancer → Client → Freelancer → Client → none. V1 → feedback → V2 → approval remains traceable. Ink/Mint, Vermilion, Acid, and Fresh Mint support the intended state meaning.

The review requested three refinements for D2.2: make the **latest submission** the strongest object on Client review; test ownership treatments that do not resemble a permanent sidebar; keep COMPLETED free of invented work or financial CTAs. D2.2 uses only the three representative states below. The full six-frame story remains in D2.1; Completed is not a new study screen here.

## Locked content and behavior

All studies use [D2_WORKFLOW_FIXTURE.md](D2_WORKFLOW_FIXTURE.md) unchanged: Build Solana Payment Infrastructure, 500 USD, North Studio as a D1 study identity, “Assigned Freelancer” as a role label, job ID `11111111-1111-4111-8111-111111111111`, V1 ID `dddddddd-dddd-4ddd-8ddd-dddddddddddd`, exact V1/V2 summary text, URLs, feedback, and timestamps. V2 appears only as a **prospective composer** in `REVISION_REQUESTED`; it is not a persisted history entry in these three states.

The backend path remains `IN_PROGRESS → submit V1 → SUBMITTED_FOR_REVIEW → request revision → REVISION_REQUESTED → submit new V2 → SUBMITTED_FOR_REVIEW → approve → COMPLETED`. `READY_FOR_REVIEW` is not an enum; `AWAITING_PAYMENT` is outside the primary D2 story. Action owners are Freelancer, Client, Freelancer for the shown states. The submission composer has only required `summary` and optional `deliverableUrl`. Client review offers **Duyệt bàn giao** as primary and **Yêu cầu chỉnh sửa** as secondary. Approval has no request body; backend capture is not a payment UI.

| Representative state | Required reading |
| --- | --- |
| 01 · FREELANCER / `IN_PROGRESS` | Job title and brief; Freelancer owns “Gửi bàn giao”; Client waits; position Working; history empty |
| 02 · CLIENT / `SUBMITTED_FOR_REVIEW` / V1 `SUBMITTED` | V1 summary, URL, status and `createdAt` dominate; Client owns approval or revision; history is visible but secondary |
| 03 · FREELANCER / `REVISION_REQUESTED` | Exact Client feedback precedes the prospective V2 composer; V1 remains in history; Freelancer owns “Gửi bản sửa” |

## Three hypotheses

- **Study A — Rail-First Editorial Workspace:** a strong horizontal rail and its adjacent action band can carry ownership without a permanent right panel. Test whether the rail is helpful or too dominant.
- **Study B — Document-First Submission Review:** the full current work document can dominate, especially on Client review; ownership and decisions can live in the document header and edge. Test whether Freelancer workspace still feels natural.
- **Study C — Handoff + Version Ledger:** a FROM → TO editorial edge and a lower version ledger can make responsibility transfer and continuity explicit. Test whether it becomes too administrative.

The studies differ in rail prominence, ownership placement, current-object scale, action location, and history treatment. They inherit D1 Cream `#FFF7E8`, Ink `#17212B`, Vermilion `#F15A3D`, Acid `#F5D12F`, and Fresh Mint `#B8DFC4` as provisional study values; Cobalt remains reserved for finance/evidence. They use no generic cards, status-pill dependency, wallet framing, or new global tokens. Motion is annotated only: confirmed updates may use opacity and at most 8px translation over 140–180ms with focus transfer. The static page and reduced-motion state remain complete without movement. Mobile is deferred.

## Human review questions

1. Which study makes **who acts now** most obvious?
2. Which makes the latest submission easiest to review?
3. Which keeps history available without letting it dominate?
4. Which makes revision feedback easiest to understand?
5. Which feels most like FreelaX rather than project-management SaaS?
6. Which can scale to V2, V3, and longer history without changing the backend contract?
7. Which keeps editorial confidence without becoming poster-like?
8. Which ownership treatment should continue into D2.3?

The human reviewer may choose A, B, C, or a controlled hybrid. No selection is made in this workpack.
