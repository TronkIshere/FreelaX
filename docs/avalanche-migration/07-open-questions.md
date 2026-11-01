# 07. Open questions, decisions and unverified assumptions

> **Scope amendment (2026-11-01):** FreelaX is a development simulation with no real funds ([SIMULATION_SCOPE](../SIMULATION_SCOPE.md)). Supported environments: LOCAL_SIMULATION and AVALANCHE_FUJI only. **Mainnet is out of scope.** Wherever this document mentions Mainnet, production, audits for launch, key ceremonies or a Mainnet cutover, read it as historical planning; see the amendment table at the end of [04](04-migration-roadmap.md) and the guards in [phase-1/simulation-environment-guards.md](phase-1/simulation-environment-guards.md). Decisions D1 to D4 are unchanged.

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

## Phase 1 update on the unverified technical facts (technical only; no business question above was closed)
Details and evidence: [phase-1/](phase-1/README.md).
| Fact | Phase 1 result |
|---|---|
| Public RPC rate limits | **Still unknown.** None seen at ~2.5 req/s; not stress-tested. |
| `eth_getLogs` range | **Measured:** official Fuji public RPC 100 blocks; publicnode 50,000 (address required). The 2,000-block hypothesis is refuted for the official endpoint. |
| `newHeads` at acceptance or execution | `newHeads` fired 0-18 ms (median 3) after `newAcceptedTransactions` for the same block on Fuji: appears to fire at acceptance. |
| `block.timestamp` under Helicon | Header seconds equal `floor(timestampMilliseconds/1000)` for 240 blocks; `eth_call` replay shows the TIMESTAMP opcode equals the header timestamp (8 blocks). Original-execution behaviour and near-deadline inclusion on Fuji **unverified**. |
| Accepted-but-unexecuted visibility via `eth_getTransactionByHash` | **Not observed; inconclusive.** Docs say receipts/state reads wait for execution. One by-hash miss in 53 observations (provider inconsistency). |
| EVM fork | Fuji: Cancun opcodes work; Pectra BLS precompile and EIP-2935 absent; Osaka CLZ invalid. Mainnet not probed. |
| ACP-283, Helicon wording | ACP-283 README still says **Proposed**; not otherwise resolved. |
| FiatToken version | Unknown. Behaviour verified instead: EIP-2612 and EIP-3009 work on Fuji USDC (fork). Mainnet behaviour unverified. |
| USDC.e address | On-chain `0xA7D7...C664` is "USDC.e", no `DOMAIN_SEPARATOR`; **no official source checked**. Both tokens report `name()` "USD Coin" (Phase 0 recorded "USDC Coin": corrected). |
| web3j 6.x on Java 21 / Boot 3.5.16 | Works **only with** `jackson-annotations:2.22` override; Java 17 untested. Release-date discrepancy not explained. |
| KMS to web3j signing recipe | Not tested (no KMS). Verification half (recover from signature) works in web3j. |
| Slither version | 0.11.6 ran on the lab contract. |
| OZ / Solidity / EIP pages | OZ 5.7.0 and solc 0.8.38 installed and used; EIP pages not re-read (behaviour tested instead). |

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

## Scope-related questions raised by the simulation-only direction (2026-11-01)
No business decision from the list above is closed by the scope constraint; these need the owner.
1. Are KMS-backed keys, key ceremonies and signer-rotation drills (D3 implementation detail, Phase 6) still wanted for a simulation, or are dev keystores enough?
2. Is an external audit (backlog 35) still wanted?
3. Which Fuji assets and faucet process do demonstrations rely on (Circle faucet limits are unverified), and who funds the demo wallets?
4. Legacy payout rails (VND off-ramp, MISA tax): kept as labelled mocks in every environment? (Unchanged: no legacy path is deleted.)
5. Does "primary chain" still mean a runtime switch between Solana and Avalanche, or Avalanche for new work with Solana as read-only history?
6. Database evidence (ADR 0001 queries): the data may be simulation data only; the queries are still needed before retiring any legacy rail.
Decisions D1 to D4: no genuine conflict found (D3 is the only one touching key custody; its role-separation principles remain valid, its KMS element is the open question above).

## Phase 1 Part B update (2026-11-01)
Resolved by evidence (technical only):
- web3j 6.x in a real Spring Boot web context: works with `jackson-annotations:2.22`, compatible with FreelaX's `jackson-bom.version` 2.21.7 pin; Jackson 2 and 3 coexist. **Java 17 cannot run web3j 6.1.0** (bytecode 65); Java 17 works with web3j 4.13.0.
- `newHeads`/duplicates/reconnect: resubscribe does not replay; overlapping subscriptions duplicate events; gaps recoverable by bounded reads.
- Official Fuji RPC throttles with HTTP 429 (HTML, `Retry-After`) after sustained probing; log ranges: official 100, publicnode 50,000 (address required, history pruned beyond roughly 10M to 30M blocks), drpc free 100.
- DER to `r,s,v` conversion and mandatory low-s normalisation validated locally.
- Environment detection: client version distinguishes a local Fuji fork (`anvil/`) from real Fuji (`avalanchego/`); chain id alone does not.
Still **OPEN (owner)**: Java 17 vs 21 for the new module; RPC provider selection; whether KMS stays deferred; scope of any external audit or key ceremony; the meaning of "primary chain"; escrow business questions 1-18 unchanged.
Still **UNRESOLVED (technical, blocked)**: own-transaction timings on Fuji, replacement bump rule on Avalanche, accepted-but-unexecuted observability, near-deadline inclusion, provider inconsistency cause, public rate-limit threshold, server-initiated WebSocket drops. Database rail mix: needs an operator.

## Owner decisions after Phase 1 Part B (2026-11-01)
- **OWNER-DECIDED:** Java 21 baseline for the Avalanche integration; web3j 6.1.0; production KMS integration deferred, ephemeral development signers (isolated by role and environment) for LOCAL_SIMULATION and AVALANCHE_FUJI. These close the earlier open items "Java 17 vs 21", "web3j version" and "KMS vendor / deferral".
- Not authorization to change production dependencies: the Jackson override (`jackson-annotations:2.22`, or BOM 2.22.4) is validated in a prototype only and must be re-validated in the actual FreelaX context.
- **Still OPEN:** final Fuji RPC provider; external audit and key ceremony scope; meaning of "primary chain"; escrow questions 1-18; whether and how legacy payment rails retire (needs DB evidence). **Still BLOCKED:** funded Fuji wallet experiments; database evidence.
