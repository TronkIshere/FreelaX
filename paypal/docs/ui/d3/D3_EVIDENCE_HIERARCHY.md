# D3.1 — Financial evidence hierarchy

The visual object is a **job-linked financial statement and evidence rail**, not an account balance. Carry the job title, party role, and stage-specific source status through every view. The read order is **financial outcome → lifecycle status → amount or estimate → supporting evidence → technical proof → tax document**. A tax document may be a first-order navigation destination while its technical identifiers remain secondary.

| Level | Purpose | Examples | Placement |
| --- | --- | --- | --- |
| **1. Human financial summary** | Answer what happened and what remains uncertain | Work approved; checkout `CAPTURED`; payout initiated; Mock USDC `CONFIRMED`; **12,300,000 VND dự kiến**; **MÔ PHỎNG — no real bank transfer**; certificate `DRAFT` | Statement headline and eight-stage rail, with explicit text and structural markers |
| **2. Supporting evidence** | Explain source amounts, timing, and destination | 500 USD source, 5 USD on-ramp fee, 495 Mock USDC, 12,375,000 gross VND, 75,000 off-ramp fee, masked TECHCOMBANK `******6789`, `DEVNET`, rate source, stage timestamps, 12,600,000 taxable income | Compact ledger rows below the relevant stage; never display all fields at equal weight |
| **3. Technical proof** | Let a participant inspect stage evidence | checkout order ID, purchase/rate/invoice/withdrawal IDs, signatures, PDAs, treasury/public keys, per-event DEVNET Explorer URLs, MISA IDs | Collapsed **Evidence details / Technical proof** per event; copy/reference action only if real data exists |

## Perspective order

**CLIENT / Thanh toán:** job and approved work → checkout `CAPTURED` → settlement started and current stage → what proof exists → tax record availability/status. The Client does not need a wallet or token-balance surface. A `COMPLETED` job is not the same as a complete off-ramp. No tax record means “Chưa có chứng từ,” not “tax exempt.”

**FREELANCER / Thu nhập:** job that generated income → current payout stage → estimated VND after the simulated off-ramp → simulation warning → masked bank destination → taxable amount and certificate status. Mock USDC and DEVNET support the explanation, but do not become a balance or send/receive controls.

## Visibility and grouping rules

- Always visible near the relevant result: job identity; stage label and status; `simulation=true` as **MÔ PHỎNG**; `DEVNET` with network evidence; **VND dự kiến** by the estimate; explicit “chưa có chuyển khoản ngân hàng thật” by simulated payout; tax `statusLabel` and whether a document is issued.
- Collapse or de-emphasize signatures, PDAs, public keys, treasury addresses, IDs, and Explorer references. Group each with its actual event: on-ramp, rate, invoice, payment, withdrawal, or completion. Never imply one signature covers all stages.
- Keep the Client checkout status in its own section, separated from `ClientPaymentStatus` in the on-chain evidence section. Keep job `taxExportStatus` separate from certificate status.
- A source error belongs to its stage. Show what failed, what previous evidence remains valid, and any supported action. Payout stages permit read-only refresh, not a user-triggered retry. Certificate sync requires `misaCertificateId`; retry export requires `EXPORT_FAILED`.
- If payout or tax data is absent, show an absent state. Do not replace null with zero, success, cash received, or a fake certificate. If `FALLBACK_PLACEHOLDER` appears, mark the exchange rate as a placeholder.
- Cream is the statement canvas; Ink carries amounts and rules. Cobalt may cue system evidence and reference details. Fresh Mint marks confirmed stages; Acid marks pending; Vermilion marks exceptions. Color accompanies text and position. These D3 study colors are **not global token lock**.

Source contract and role routing: [D3_FINANCIAL_CONTRACT_AUDIT.md](D3_FINANCIAL_CONTRACT_AUDIT.md), `SCREEN_ARCHITECTURE.md` C08/F10/F12. Fixture: [D3_FINANCIAL_FIXTURE.md](D3_FINANCIAL_FIXTURE.md).
