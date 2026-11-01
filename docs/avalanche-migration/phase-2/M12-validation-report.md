# P2-M12 report: Phase 2 acceptance

Date: 2026-11-02. **Final status: IMPLEMENTATION COMPLETE WITH EXTERNAL VALIDATION PENDING.** Not "fully validated": every criterion that needs a real Fuji signature is BLOCKED, and several items below are PARTIAL or NOT IMPLEMENTED. Phase 1 remains PARTIAL. Phase 3 and production work were not started. Nothing was pushed.

## 1. Regression run in this milestone (2026-11-02)
| Check | Result |
|---|---|
| Full Maven reactor on JDK 21 with Docker (Anvil and MySQL via Testcontainers) | BUILD SUCCESS: payment-backend 72, marketplace-backend 381, misa 20, solana-integration 10 (Solana gateway), avalanche-integration 104 (101 pass, 3 optional skipped); 0 failures, 0 errors |
| JDK 17 reactor (`solana-integration`, `marketplace-backend`, parent) | BUILD SUCCESS (compile, 4-module reactor preserved; no Avalanche module on JDK 17) |
| Foundry 1.8.7 (pinned image) | 54 pass, 0 fail, 1 skipped (optional Fuji fork test, needs `FUJI_RPC`) |
| Frontend `tsc -b`, `vite build`, Vitest | clean, built, 345 tests pass in 15 files; **13 files cannot load on Node 22.10.0** (ESM-only `uuid` required by `@solana/web3.js` transitive dependency; reproduced without any Phase 2 change). The Solana frontend regression is therefore **not verifiable here** |
| Secret scan of tracked files (private-key, mnemonic, PEM patterns) | no matches; `.env` never read |
| Mainnet references in main sources | only deny-list guards (`EnvironmentGuard`, `Deploy.s.sol`) |
| `docker compose config` | cannot be evaluated without the owner's ignored `.env` (it requires `DEMO_FREELANCER_PASSWORD`, an existing requirement); **not validated** |
| Fuji read-only | no new Fuji request in M10 to M12; earlier read-only evidence (chain id, client version, USDC inspection, fork-based O-6 tests) is in the M1/M6/M7 reports |

## 2. Acceptance classification
| Area | Criterion | Class | Evidence |
|---|---|---|---|
| Foundation | module, JDK 21 isolation, Jackson, Spring+web3j call | PASS | M1, reactor above |
| Safety | environment guards, Mainnet rejection, host allow-list, no Fuji signer, ephemeral LOCAL signers | PASS | M1, M2, M7 tests |
| Transactions | idempotency, raw tx before send, UNKNOWN on timeout, REVERTED, REPLACED rules, nonce allocation, restart | PASS | M3 (real Anvil + MySQL) |
| Scanner | cursor, crash equals single pass, duplicates, range halving, Retry-After, WebSocket hints only, second provider | PASS (LOCAL) | M4. Finalized-tag path on Fuji untested |
| Contract | funding, extension, submission, revision, release, dispute, resolve, mutual refund, pause, roles, invariants | PASS | M5, M6 (54 Foundry tests), Slither triaged |
| Java/Solidity parity | EIP-712 digests, ABI export, reconciler | PASS | M6 |
| Deployment | deploy script with chain guard, manifest verification, allow-list, local deploy | PASS (LOCAL) | M7 |
| Deployment on Fuji | deploy, role grant, transactions | **BLOCKED** | no human-operated signing procedure; backend must not sign on Fuji |
| Gateway API | endpoints, idempotent execute, key filter, strict JSON | PASS | M8 |
| Marketplace | additive migration, flag, authorization from records, FINALIZED-only settlement, Solana refusal | PASS | M9 |
| Marketplace | fees, MISA tax, payouts, notifications for the Avalanche rail | **NOT IMPLEMENTED** | M9 section 4; owner decision needed |
| Marketplace | chain-event mirror of Submitted/Revision/Disputed into milestone status | **NOT IMPLEMENTED** | M9 |
| Frontend | EIP-1193 adapter, chain allow-list, typed-data/transaction validation | PASS | M10 (24 tests) |
| Frontend | banner, badges, ten states, explorer links, fund/extension-approve/release | PASS | M11 (10 tests) |
| Frontend | submit, revise, dispute, resolve, extension-request, mutual-refund UI; admin screen; legacy labelling | **NOT IMPLEMENTED** | M11 section 2 |
| Frontend | browser wallet test | **NOT IMPLEMENTED** | no wallet extension available |
| End-to-end | cross-service run (marketplace + gateway + Anvil + MySQL in one environment) | **NOT IMPLEMENTED** | covered per layer (marketplace vs mocked gateway; gateway vs real Anvil/MySQL); the HTTP contract between them is asserted on both sides only |
| End-to-end | fault injection (timeout, 429, WebSocket drop, duplicate, missed event, revert, nonce conflict, restart, invalid signer/env/contract) | PARTIAL | each is covered by the layer tests named in the M3/M4/M7/M8 reports; no single combined scenario run and no marketplace-level injection |
| Fuji | read-only identity/contract inspection | PASS (earlier milestones) | not repeated here |
| Fuji | any transaction-dependent criterion, authorized prepare on Fuji, calibration, USDC blacklist on-chain behaviour beyond the fork | **BLOCKED** | needs human-operated signing |
| Coexistence | Solana backend regression (Java) | PASS | 10/10 gateway, marketplace 381, payment 72 |
| Coexistence | Solana frontend regression | **PARTIAL (unverifiable)** | 13 suites blocked by Node 22.10 |
| Packaging | compose validation, CI | **NOT VALIDATED / not present** | needs owner `.env`; no CI configuration exists |

## 3. Unresolved decisions and human actions
1. Provide a human-operated Fuji signing procedure (deployer and role holders, funded testnet wallet) before any Fuji deployment; run the optional Fuji fork test with `FUJI_RPC`.
2. O-2 shared-vs-factory contract, O-6 acceptance of USDC blacklist behaviour, O-8 RPC provider, O-13 audit/key ceremony.
3. Fee, tax, payout and notification treatment for an Avalanche release; scanner-driven status mirror or marketplace-driven.
4. Upgrade Node to >= 22.12 (or decide on the dependency) and re-run the frontend suites; add CI with JDK 21 and Docker.
5. Review Vietnamese UI copy; finish the remaining action screens.
6. LOCAL signer addresses change per process: a local deployment must grant roles to the current signer addresses.
