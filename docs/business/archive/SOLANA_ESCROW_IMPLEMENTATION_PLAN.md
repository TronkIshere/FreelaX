# Solana milestone escrow — implementation and verification status

> **Lưu trữ theo mốc ngày.** Xem [tổng quan hiện tại](../README.md) và [trạng thái kiểm chứng](../VERIFICATION.md) trước khi dùng các kết luận bên dưới.

Branch: `feat/solana-milestone-escrow`. Scope: one milestone per Job, one configured six-decimal token mint, full release or full refund. The existing simulated payment rail remains separate.

**Business boundary (2026-10-08 update):** this plan covers **token custody in a Solana vault**. It does not implement the newly agreed partner-held USD → bank-paid VND flow. That target flow uses a separate mock partner statement and a **3% FreelaX fee charged to the Freelancer only after confirmed payout**; a pre-payout refund returns the full deposited USD and charges no FreelaX fee. The current Solana settle instruction transfers the full token amount and must not be presented as implementing that fee or a bank payout. See [partner flow and business rules](PARTNER_ESCROW_BUSINESS_GAP_20261008.md).

**Status:** implementation is present across Anchor, Solana Gateway, Marketplace and frontend on the current branch. This is beyond the frozen P06 baseline. Local-validator approved release, mutual refund, scheduler timeout release and Admin dispute refund have passed. The broad regression gate, remaining edge-case matrix, Solana browser E2E and devnet demo remain open. A checked item means the stated scope was verified; it does not mean production deployment.

**Local E2E history (2026-10-08):** [release, refund and timeout reconciliation results](SOLANA_ESCROW_LOCAL_E2E_20261008.md). Client-approved release and mutual refund passed through Marketplace → Gateway → Anchor with verified token balances. Permissionless timeout release and Marketplace reconciliation passed after a direct Gateway claim. At that date, Marketplace scheduler initiation and Admin dispute refund remained open; both were verified in the later result below.

**Status review (2026-10-09):** the local Marketplace → Gateway → Anchor approved-release and mutual-refund paths were rerun with Mock USDC and passed. Two new Jobs then passed [scheduler timeout release and Admin dispute refund](SOLANA_ESCROW_GATE_E2E_20261009.md), including vault/token-balance checks. The separate `PARTNER_ESCROW_MOCK` rail passed HTTP E2E release/refund/outage recovery and a browser E2E using seeded accounts for funding → submission → approval → payout plus Admin matched/mismatch views. That browser test creates the Job, application and assignment through API; fresh-account registration and full Job creation in the browser are documented for manual testing but are not covered by that automated gate.

## 1. On-chain escrow and timeout release

- [x] Add a Milestone Escrow PDA and PDA-owned token vault. Client signs funding for the exact milestone amount. Freeze client, freelancer, mint, amount, funding/delivery deadline, review window and arbiter in the escrow account.
- [x] Record a Freelancer-signed submission hash on-chain, with Marketplace co-signature after validating and persisting the payload. The on-chain clock starts its review window. Only a submission confirmed on-chain counts for the escrow rail.
- [x] Let Client approve or request a revision before the current review deadline. A revision commits a feedback hash, cancels the prior timeout and a fresh submission creates a new deadline. Let either participant open a dispute before timeout, or after the delivery deadline if there is no submission.
- [x] Add permissionless timeout release through `settle_milestone_escrow(release=true)`: after the on-chain review deadline and with no dispute or pending revision, a caller pays the transaction fee and the PDA transfers the full amount to the Freelancer. No Client release signature is required. Add mutual refund and Admin resolution for an open dispute. Terminal states reject repeats.
- [x] Add one Freelancer-requested, Client-approved delivery extension before the original deadline, capped at seven days after the original deadline. An absent first submission after the effective deadline is locked and requires a dispute decision.
- [ ] Test signer, mint, amount, deadline, dispute/revision race, duplicate transfer, timeout and restart scenarios on local validator.

## 2. Gateway and wallet transactions

- [x] Build and submit the new Anchor instructions, derive and decode escrow/vault accounts, and expose confirmed account state plus transaction signatures. Verify program ownership, mint, parties, amount and token account balances.
- [x] Build user-authorized transactions for Client/Freelancer actions. Keep private wallet keys in the wallet; do not let a backend key impersonate participants. A backend fee payer may call permissionless timeout release.
- [x] Reconcile ambiguous RPC outcomes by reading the same PDA and signature before any retry. Never report settlement succeeded from a transaction submission response alone. Missing off-chain submissions/revisions/disputes are reconstructed from persisted intent or, for a direct on-chain dispute, a factual placeholder.

## 3. Marketplace state machine

- [x] Introduce an explicit `SOLANA_ESCROW` payment rail and persistence for wallet addresses, escrow PDA, signatures and reconciliation state. Existing simulation records cannot be reused as proof of token movement.
- [x] Activate work only after on-chain funding is confirmed. Only a confirmed on-chain submission starts the review clock; mirror extension, revision and dispute decisions on-chain before changing authoritative business status.
- [x] Scheduler calls permissionless timeout release after the on-chain deadline; Freelancer can also trigger it. Record `RELEASE_PENDING` while transaction is unresolved; complete Job only after verified vault transfer. Preserve 72-hour review for amounts up to USD 500 and 96 hours for larger amounts, using a frozen review window in the escrow account.
- [x] Funding reminder at 24 hours and 48-hour on-chain funding expiry after assignment; backend waits 15 minutes before closing an unresolved unfunded build. Lock first submissions after the effective delivery deadline. Admin resolves disputed escrow with full release or full refund.

## 4. Frontend

- [x] Connect and bind Client/Freelancer wallets with a signed challenge, display the connected address and configured cluster, and request signatures for funding, submission, extension, review, dispute and mutual refund. Show a confirmation screen with mint, exact amount and recipient before funding.
- [x] On Funding, show vault address, balance, funding transaction and confirmation/reconciliation state. On Work Detail, show effective deadline, submission hash, review countdown, extension, revision, dispute and timeout claim button for Freelancer.
- [x] On Finance, show release/refund signatures and explorer links when a known public cluster is configured. Distinguish `awaiting confirmation`, `funded`, `release eligible`, `release pending`, `retry pending` and `settled`; never infer success from a click or local clock alone. Simulated Jobs retain their current UI.

## 5. Verification and demo

- [x] Exercise two new Jobs end-to-end on local validator: scheduler timeout release after Client silence and Admin dispute refund. Verify Marketplace/chain terminal state and vault/token balances. Admin resolution signature was confirmed through RPC; the timeout signature was stored by Marketplace but RPC history was unavailable after validator restart. See [evidence and limits](SOLANA_ESCROW_GATE_E2E_20261009.md).
- [ ] Rerun broad Anchor, Gateway, Marketplace and frontend checks on the current working tree; complete signer, mint, amount, deadline, dispute/revision race, duplicate transfer and restart matrix. Solana browser UI E2E also remains open.
- [ ] Repeat on devnet using the deployed Program ID and configured mock token; label demo assets accurately. Record compute units, signatures and account state. Deployment and use of externally controlled funds require separate explicit authorization.

The partner-held USD MVP has its **own** end-to-end gate: Client funding is confirmed from an independent mock partner statement before work starts; payout confirms VND to the Freelancer and records the 3% fee only once; refund restores the full USD with zero FreelaX fee; the admin reconciliation shows both a matching balance and an intentional mismatch. The mock API checks and seeded-account browser checks above cover these cases at different layers; see [partner local E2E evidence](PARTNER_MOCK_LOCAL_E2E_20261009.md). Fresh-account signup through final payout remains a manual guide, not an automated browser result. None of the Solana checks above satisfy this partner gate.

## Rules for ambiguous states

On-chain state and finalized token transfer determine escrow money status. Solana programs do not run automatically at a wall-clock time; a keeper or user must submit `claim_timeout_release`. An off-chain dispute/request_revision that has not landed on-chain cannot block a timeout claim. The UI must show that race clearly. Submission hashes prove which content was committed and when, not whether the deliverable meets quality criteria.
