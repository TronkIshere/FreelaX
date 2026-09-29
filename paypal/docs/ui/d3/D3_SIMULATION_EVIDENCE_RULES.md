# D3.1 — Simulation and evidence honesty rules

These rules apply to Client Payment Detail, Freelancer Payout Detail, Tax Records, and Certificate Detail. They preserve the source contract while the D3 grammar remains a static design study.

| Source condition | Required visible wording | Claim to avoid |
| --- | --- | --- |
| `simulation=true` | **MÔ PHỎNG / SIMULATION** beside financial outcome and in stage 06 | Live settlement or actual cash receipt |
| `network=devnet` | **DEVNET** by on-chain evidence and any Explorer reference | Mainnet or production settlement |
| `OnRampStatus.CONFIRMED` | **Mock on-ramp; 495 Mock USDC confirmed** in the fixture | Real fiat purchase or spendable balance |
| `estimatedAmountVnd` | **12,300,000 VND dự kiến / Estimated VND**, after fixture fee | “Money received”, “available balance”, or guaranteed payout |
| `OffRampStatus.SIMULATED` | **Simulated bank payout; no real bank transfer** | Funds reached bank account |
| `OffRampStatus.COMPLETED` | **Simulated/on-chain completion record completed; bank transfer not verified** | Real-world bank transfer completed |
| `usdcToVndRateSource` / `taxRateSource` | Name the relevant USDC/VND or USD/VND source separately | One rate explains both estimates |
| `FALLBACK_PLACEHOLDER` | **Tỷ giá giả lập / placeholder rate** directly by affected number | Live market quote |
| Explorer URL returned for DEVNET signature | **DEVNET technical evidence for this event** | Proof of fiat bank settlement |
| Tax record `DRAFT` | **Đã lập chứng từ, chưa phát hành** | Issued or accepted certificate |
| Certificate `PENDING_EXPORT` or `EXPORT_FAILED` | Use source `statusLabel`; show no issued-document claim | An issued document exists merely because a record exists |

The fixed fixture uses `LIVE_COINGECKO` for the illustrative payout conversion source and `LIVE_OPEN_ER_API` for the separate illustrative tax rate. Neither fixture number is a live fetch. Only an actual returned source value and amount may be used in an implementation. The backend masks the bank account; the study shows only `******6789`.

Failure language is stage-specific: **mock on-ramp failed**, **on-chain Client payment failed**, **withdrawal request failed**, **simulated VND payout failed**, **tax export failed**, or the exact certificate `statusLabel`. Preserve confirmed checkout/work and earlier evidence. Never collapse all of these into “Payment Failed.” A `PAYOUT_FAILED` notification does not erase `checkoutOrderStatus=CAPTURED`.

No generic **Retry payout**, **Retry on-ramp**, or **Retry withdrawal** action is supported by the participant payment-status API. Tax **Sync status** is valid only with `misaCertificateId`; **Retry export** only for `EXPORT_FAILED`. PDF/XML download requires a MISA certificate ID and may still fail upstream. Do not add manual certificate creation, signing, submission, correction, approval, or tax-rate editing.

Prohibited unless a future verified source supports the exact claim: **Money received**, **Bank transfer completed**, **Available balance**, **Withdraw now**, **Cash balance**, **Send**, **Receive**, **Swap**, **Buy crypto**, **Deposit**, and an unqualified **Payment complete** across all four tracks. No generic wallet, QR, card, trading, yield, or portfolio framing.

The fixture identifiers beginning `FIXTURE-` are deliberately non-live. Never turn them into clickable Explorer URLs. A live DEVNET URL, if returned by the backend, belongs under its specific event's technical proof, not in the outcome heading.
