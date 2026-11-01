# FreelaX: Solana to Avalanche C-Chain migration (Phase 0 planning)

Status: **Phase 0 (research and architecture) complete. No implementation exists.** Prepared 2026-11-01 against `master` @ `0f263b7`. Phase 1 must not start until this plan is approved.

## Objective
Make Avalanche C-Chain the primary blockchain integration of FreelaX. Solana code, modules, docs and historical references stay intact as a legacy/reference implementation. Avalanche is built independently, tested independently, integrated progressively; only the **final phase** switches the primary chain.

## Executive summary
- Solana coupling is wide but shallow in business terms: ~15 services inject `SolanaCprClient` directly; the neutral `BlockchainGateway` has 3 methods and one consumer (see [00](00-current-architecture-audit.md)).
- Important finding: the escrow rules (72h review, one extension, 2 revisions, dispute timing, auto-release after deadline, arbiter-only dispute settlement) are **enforced on-chain** by the Anchor program today. The backend mirrors them. A Solidity escrow therefore must reproduce a compact version of that rule set, not just be a vault.
- Avalanche C-Chain is technically suitable: EVM, native 6-decimal USDC, mature tooling (Foundry, OpenZeppelin, web3j, viem). It is not clearly "better" on every axis (see [02](02-solana-vs-avalanche.md)). The justification is ecosystem/tooling/operations fit, and it must be confirmed by the open questions in [07](07-open-questions.md).
- Since the Helicon upgrade (active on Fuji and Mainnet per the research below) C-Chain separates **accepted** from **executed** transactions. Confirmation logic must key on the receipt (and optionally `finalized`), never on "submitted" or on empty `eth_getLogs` near head ([01](01-avalanche-technical-research.md) section 3).
- Recommended stack: one shared immutable Solidity escrow, Foundry, OpenZeppelin, web3j as ABI/signing/RPC layer with a DB-backed nonce manager, KMS-backed backend signer, viem/wagmi on the frontend, direct log scanning with confirmations (no external indexer initially).
- No persisted enum or API contract has to change in Phase 0. Chain-flavoured columns get forward-only additions later, never renames.

## Phase overview
| Phase | Goal | Touches primary payment flow? |
|---|---|---|
| 0 | Research, architecture, plan (this set) | No |
| 1 | Avalanche environment and foundation setup | No |
| 2 | Standalone Avalanche module | No |
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
| [03-target-architecture.md](03-target-architecture.md) | Module boundaries, interfaces, Mermaid diagrams, migration matrix |
| [04-migration-roadmap.md](04-migration-roadmap.md) | Phases 0 to Final with the required 16 headings |
| [05-risk-register.md](05-risk-register.md) | Risks and mitigations |
| [06-implementation-backlog.md](06-implementation-backlog.md) | Ordered tasks |
| [07-open-questions.md](07-open-questions.md) | Unverified assumptions and experiments |

## Key architectural decisions (proposed, pending approval)
1. C-Chain only; no custom Avalanche L1.
2. Avalanche lives in a **new sibling module** (`avalanche-integration` gateway) and a new `blockchain/avalanche` adapter package; Solana modules are not renamed or edited beyond neutral-port wiring.
3. Extend `BlockchainGateway` only when a consumer moves behind it (existing ADR rule, [ADR 0001](../adr/0001-solana-to-avalanche-migration.md)).
4. Assets identified by `(chainId, contract address)` from explicit configuration, never by symbol.
5. Business state stays in MySQL; on-chain state is authoritative only for escrow custody, timing and arbiter resolution.
6. Persisted enums are unchanged; EVM details (tx hash, nonce, block) are added as new columns in a forward-only Flyway migration in Phase 4.

## Source reliability note
Research was performed by reading official pages and querying public RPC endpoints on 2026-11-01. Each claim in [01](01-avalanche-technical-research.md) is tagged **V** (verified), **S** (snippet or secondary), or **U** (unverified/assumption). Values tagged S or U, and every address, must be re-verified in Phase 1 before use in configuration.
