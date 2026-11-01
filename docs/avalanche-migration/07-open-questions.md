# 07. Open questions, decisions and unverified assumptions

## Blocking questions from revision 1: status
| # | Question | Status | Notes |
|---|---|---|---|
| 1 | Why Avalanche? | **DECIDED (D1)** | Engineering/product-architecture decision; no external driver recorded; no speed or cost claim. Add any external driver to the README register if it exists. |
| 2 | Is per-job fund isolation required? | **DECIDED (D2)**, with a Phase 3 gate | Mandatory at accounting/authorization level; baseline shared immutable contract. Checked against code: Solana has physical per-escrow vaults, so the shared design loses address-level isolation; the factory/clone comparison remains open. |
| 3 | Replace the backend co-signature | **DECIDED in principle (D3)**; details open | Checked against code: co-sign occurs on exactly `fund`, `submit`, `request_revision`. Mapping in [03](03-target-architecture.md) sections 7 and 9. |
| 4 | Do escrow rules stay on-chain? | **DECIDED (D4)** with clarifications | Checked against code: rules are on-chain; 72h and `max_revisions` are bounded parameters, not constants; pause blocks only funding; release after the deadline is already permissionless. |

Technical questions are **not** resolved by these decisions. They remain open below.

## Open design questions (escrow, from reading `milestone_escrow.rs`; do not invent rules)
1. **Arbiter model:** Solana snapshots `config.admin` as the arbiter at fund time. On EVM, role-based (admin can rotate) vs per-escrow snapshot (rotation impossible, lost key strands Disputed escrows). Preference is role-based, needs approval.
2. **Revision co-sign:** keep the backend authorization on `requestRevision` for parity, or eliminate it (client-only, bounded by 2 revisions)? Product decision.
3. **72h as constant vs parameter:** preserve as a bounded parameter (24-168h) bound by the backend authorization (current behaviour), or fix 72h in the contract for the unified rail (a behaviour change).
4. **Revision state has no deadline:** after a revision request the freelancer may never resubmit and funds sit until dispute or mutual refund. Preserve or define a rule (needs approval).
5. **No unilateral refund after a missed delivery date:** only dispute (allowed after `delivery_due_at`) or mutual refund. Preserve?
6. **Dispute outcome is binary** (full release or refund). Partial split is not supported today; preserve?
7. **A pending extension request never expires or can be rejected.** Preserve?
8. **Mutual refund is unavailable once Disputed.** Preserve?
9. **Payout when USDC blacklists a recipient or the contract:** pull payout, claimable balance, admin reroute. Not defined in Anchor; needs a design that does not give any role access to funds.
10. **Mutual refund mechanism:** two EIP-712 signatures submitted by anyone vs two separate on-chain approvals.
11. **`highValueGrace` (+24h above 500 USDC):** the legacy escrow uses it; the unified rail passes false. Keep as a parameter?
12. **Shared contract vs factory/clones:** decided by the Phase 3 gate ([03](03-target-architecture.md) section 10).
13. **Gas payer:** users pay AVAX, or a relayer or sponsor? (Solana currently sponsors fees.) The relayer for `release` is convenience only either way.
14. **Admin multisig membership, timelock use, and who holds the arbiter and authorization keys.**
15. **New rail literal** (for example `AVALANCHE_ESCROW`) vs reuse of `UNIFIED_USDC_PAYOUT` plus a chain column.
16. **Legacy invoice/off-ramp path on Avalanche:** needed or moved off-chain? Requires read-only SQL evidence and product input.
17. **In-flight Solana escrows at cutover** (proposal: they finish on Solana).
18. **Mock on-ramp replacement on Avalanche** (Fuji test USDC only; no real on-ramp exists in the repo).

## Unverified technical facts (from [01](01-avalanche-technical-research.md))
- Public RPC rate limits and `eth_getLogs` range.
- Whether `newHeads` fires at acceptance or execution after Helicon.
- **`block.timestamp` semantics under Helicon's asynchronous execution** and the effect on deadline checks (new, needed for the escrow timing rules).
- **Whether an accepted-but-unexecuted transaction is visible through `eth_getTransactionByHash`** (new; the tracking design does not depend on it).
- Current EVM fork on C-Chain (Cancun vs later) and compiler settings.
- ACP-283 status; Helicon page "Scheduled" vs active wording.
- Fuji AVAX faucet limits; current Snowtrace/Routescan status and verification flow.
- Exact FiatToken version behind mainnet and Fuji USDC proxies.
- Official source for the USDC.e address (the address in 01 is recalled, not verified).
- web3j 6.x on Java 21 and Spring Boot 3.5.16; why Maven Central and GitHub release dates differ.
- KMS to web3j signing recipe including EIP-712 digests; Vault secp256k1 support; AvalancheGo self-hosting requirements; current Slither version.
- OpenZeppelin, Solidity and EIP pages were not fetched.
- Core Wallet and `wallet_addEthereumChain` specifics; Reown package names.

## Repository unknowns
- Which rails hold production data (ADR SQL queries not yet run).
- Partner rail's path through `SettlementDownstreamService`.
- misa-backend migrations; payment-backend internals.
- UI caller of `/solana/wallet-link/challenge|verify`.

## Experiments required (Phase 1)
- Fuji: send tx and measure time to receipt and to `finalized`; observe `eth_getLogs` before execution; compare `newHeads` and `newAcceptedTransactions`; observe `block.timestamp` of a transaction submitted just before a contract-level deadline.
- Fork test against Fuji USDC: approve and `transferFrom`, permit, blacklist behaviour (if testable).
- KMS secp256k1 signing to a valid Fuji tx and to an EIP-712 digest.
- Public RPC and chosen provider rate-limit probing.
- Accepted-then-reverted transaction handling.

## Needed before Phase 1
Approval of revision 2, read-only SQL access for rail evidence, accounts for an RPC provider and a KMS, and an explicit answer (or "preserve as is") on questions 1-8 above before Phase 3 starts.
