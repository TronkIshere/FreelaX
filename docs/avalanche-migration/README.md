# FreelaX: Solana to Avalanche C-Chain migration (Phase 0 planning)

Status: **Phase 0 (research and architecture) complete; architectural decisions D1-D4 recorded (revision 2).** No implementation exists. Prepared 2026-11-01 against `master`. Phase 1 must not start until this plan is approved.

## Objective
Make Avalanche C-Chain the primary blockchain integration of FreelaX. Solana code, modules, docs and historical references stay intact as a legacy/reference implementation. Avalanche is built independently, tested independently, integrated progressively; only the **final phase** switches the primary chain.

## Executive summary
- Solana coupling is wide but shallow in business terms: ~15 services inject `SolanaCprClient` directly; the neutral `BlockchainGateway` has 3 methods and one consumer ([00](00-current-architecture-audit.md)).
- The escrow rules (review window, one extension, revision limit, dispute timing, auto-release after the deadline, arbiter-only dispute settlement) are **enforced on-chain** by the Anchor program today. Decision D4 keeps them on-chain: the Solidity escrow is not a generic vault.
- Avalanche C-Chain is chosen as an **engineering and product-architecture decision** (D1). No measured performance or cost advantage has been established, and none is claimed ([02](02-solana-vs-avalanche.md)).
- Since the Helicon upgrade (reported active on Fuji and Mainnet) C-Chain separates **accepted** from **executed** transactions. Confirmation keys on the receipt (and `finalized`), never on submission or on empty `eth_getLogs` near head ([01](01-avalanche-technical-research.md) section 3, [03](03-target-architecture.md) section 8).
- The single Solana admin key (config admin, arbiter, co-signer, auto-claimer, dispute resolver) is **not** reproduced (D3). Roles are separated; the backend gets only a constrained EIP-712 authorization signer ([03](03-target-architecture.md) sections 7-9).
- Recommended stack: shared immutable Solidity escrow with logically isolated per-escrow records (D2), Foundry, OpenZeppelin, web3j as ABI/signing/RPC layer with a DB-backed nonce manager, KMS-backed signers, viem/wagmi on the frontend, direct log scanning (no external indexer initially).
- No persisted enum or API contract changes in Phase 0. Chain details are added as new columns later, forward-only, never renames.

## Decision register
| ID | Decision | Status | Verified against source | Remaining open |
|---|---|---|---|---|
| D1 | Why Avalanche: EVM/Solidity ecosystem, tooling and security tooling, native USDC, Java via EVM libraries, standard wallets, long-term maintainability and potential multi-EVM reach. Engineering/product decision; **no external business requirement, partnership or grant is recorded**; no speed/cost claim. | **DECIDED** | n/a (rationale, not code). Consistent with [02](02-solana-vs-avalanche.md). | If an external driver exists, record it here. |
| D2 | Per-escrow fund isolation is mandatory at contract accounting/authorization level; initial design = single shared immutable escrow with isolated records; factory/clone only if a concrete benefit is shown. | **DECIDED** (shared contract as the baseline) | Confirmed: Solana escrow is per-milestone (`milestone_id` UUID seed; `escrow_contracts` unique on contract and milestone). **Difference found:** Solana gives each escrow its own vault token account (PDA-owned ATA), i.e. physical per-escrow custody; a shared EVM contract gives logical isolation only. | Phase 3 design gate: compare factory/clone (blacklist blast radius, verifiability) vs shared ([03](03-target-architecture.md) section 6). |
| D3 | Do not reproduce the single admin key; separate roles; EIP-712 backend authorization bound to chain, contract, escrow, action, nonce, deadline, parameters; backend signer cannot withdraw or override disputes. | **DECIDED** (principles and per-instruction mapping); implementation details open | Confirmed: marketplace authority co-signs exactly three instructions (`fund_milestone_escrow`, `submit_escrow_work`, `request_escrow_revision`); also `config.admin` is the arbiter snapshotted at fund. | Revision co-sign necessity; arbiter snapshot vs role; see [07](07-open-questions.md). |
| D4 | Keep security-critical escrow rules on-chain; permissionless eligible release; backend relayer is convenience only. | **DECIDED** with clarifications below | Confirmed: release after `review_due_at` is already permissionless on Solana (`actor == client \|\| now >= due`). | Ambiguities listed in [07](07-open-questions.md). |

### Clarifications where decisions meet the actual code
1. **72h and max revisions are parameters on-chain, not constants.** The Anchor program bounds the review window to 24-168h and `max_revisions` to at most 2; 72h and 2 are chosen by the backend (`PaymentFlow.REVIEW_WINDOW_HOURS = 72`, `Job.maxRevisions = 2`) and checked back by the backend against chain state. Preserving behaviour means the same bounded parameters, with the backend EIP-712 authorization binding the exact values; making 72h a contract constant would be a behaviour change that needs explicit approval.
2. **Pause only blocks new funding on Solana** (`SystemPaused` check on `fund_milestone_escrow` and on invoice/on-ramp instructions, none on settle, refund or dispute). The EVM design keeps that: pause must never freeze release/refund of existing escrows.
3. **Backend availability today:** release does not need the backend; `fund`, `submit` and `request_revision` do (co-sign). The backend scheduler's auto-claim is a convenience; any wallet can claim after the deadline.
4. **Timing boundaries are exact and mutually exclusive** (strict vs non-strict comparisons) and must be copied verbatim ([03](03-target-architecture.md) section 7).
5. Escrow ambiguities in the current code (Revision state has no deadline, no unilateral refund after a missed delivery date, dispute outcome is binary, pending extension requests never expire) are **recorded, not resolved**.
6. **Legacy rails:** no legacy payout path is migrated or deleted without read-only SQL evidence ([00](00-current-architecture-audit.md) section 6). Initial Avalanche scope = the escrow part of `SOLANA_ESCROW` and `UNIFIED_USDC_PAYOUT`; `PARTNER_ESCROW_MOCK` and the legacy null rail's fiat funding need no chain work; the legacy downstream payout stays on Solana.

## Phase overview
| Phase | Goal | Touches primary payment flow? |
|---|---|---|
| 0 | Research, architecture, plan (this set) | No |
| 1 | Avalanche environment and foundation setup | No |
| 2 | Standalone Avalanche module (incl. signer design implemented behind interfaces) | No |
| 3 | Solidity escrow + contract tests | No |
| 4 | Backend integration behind `BlockchainGateway` | Only behind a disabled/secondary config |
| 5 | Frontend EVM wallet support (Solana kept) | Secondary |
| 6 | E2E validation, production readiness, release gates | Validation only |
| Final | Switch primary chain to Avalanche, rollback tested | Yes |

Details: [04-migration-roadmap.md](04-migration-roadmap.md).

## Document index
| File | Content |
|---|---|
| [00-current-architecture-audit.md](00-current-architecture-audit.md) | Verified architecture, Solana dependency inventory, rails, DB, business constraints |
| [01-avalanche-technical-research.md](01-avalanche-technical-research.md) | C-Chain, Helicon, lifecycle, Fuji, USDC, EVM stack, wallets, Java, RPC |
| [02-solana-vs-avalanche.md](02-solana-vs-avalanche.md) | Objective comparison, gains and losses |
| [03-target-architecture.md](03-target-architecture.md) | Module boundaries, diagrams, migration matrix, role-separated signing, Anchor-to-Solidity mapping, transaction tracking |
| [04-migration-roadmap.md](04-migration-roadmap.md) | Phases 0 to Final with the required 16 headings |
| [05-risk-register.md](05-risk-register.md) | Risks and mitigations |
| [06-implementation-backlog.md](06-implementation-backlog.md) | Ordered tasks |
| [07-open-questions.md](07-open-questions.md) | Decisions, unverified assumptions, experiments |

## Key architectural decisions
1. C-Chain only; no custom Avalanche L1.
2. Avalanche lives in a **new sibling module** (`avalanche-integration` gateway) and a new `blockchain/avalanche` adapter package; Solana modules are not renamed or edited beyond neutral-port wiring.
3. Extend `BlockchainGateway` only when a consumer moves behind it ([ADR 0001](../adr/0001-solana-to-avalanche-migration.md)).
4. Assets identified by `(chainId, contract address)` from explicit configuration, never by symbol.
5. Business state stays in MySQL; on-chain state is authoritative for escrow custody, status, deadlines, counters and arbiter resolution (D4).
6. Persisted enums are unchanged; EVM details are added as new columns via a forward-only Flyway migration in Phase 4.
7. Role-separated signing with least privilege; no key can both authorize terms and move funds (D3).
8. Escrow isolation: logical per-escrow accounting in a shared immutable contract, with a Phase 3 gate to re-evaluate factory/clones (D2).

## Source reliability note
Research was performed by reading official pages and querying public RPC endpoints on 2026-11-01. Each claim in [01](01-avalanche-technical-research.md) is tagged **V**, **S** or **U**. Values tagged S or U, and every address, must be re-verified in Phase 1 before use in configuration. Having decided an architectural preference does not resolve a technical question: those stay open in [07](07-open-questions.md).
