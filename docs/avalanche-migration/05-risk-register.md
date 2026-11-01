# 05. Risk register

Likelihood (L) and impact (I): Low, Medium, High. Rows reference phases in [04](04-migration-roadmap.md).

## Smart contract risks
| # | Risk | L | I | Mitigation |
|---|---|---|---|---|
| S1 | Reentrancy or logic bug in escrow | M | H | `ReentrancyGuard`, checks-effects-interactions, `SafeERC20`, fuzz/invariant tests, Slither, external audit before Mainnet |
| S2 | Unauthorized release/refund | M | H | `AccessControl` roles, arbiter only for Disputed, auto-claim only after `reviewDueAt`, tests per rule |
| S3 | Admin/contract owner key compromise | M | H | multisig admin, KMS operator key, pause, no unilateral fund movement beyond defined arbiter powers |
| S4 | Wrong token or USDC.e accepted | M | H | token pinned in contract, backend `(chainId,address)` config, decimals assertion |
| S5 | USDC blacklist/pause/upgrade locks funds | M | H | pull payouts, admin reroute for blacklisted payee, documented issuer-freeze assumption |
| S6 | Approval race / excessive allowance | M | M | exact-amount approvals, UI shows spender and amount |
| S7 | EIP-712 domain or replay mistakes | L | H | include chainId, verifyingContract, deadline, nonce; test cross-chain replay |
| S8 | Semantics diverge from Anchor rules | M | M | rule-to-test mapping table, scenario tests copied from Anchor specs |

## Backend risks
| # | Risk | L | I | Mitigation |
|---|---|---|---|---|
| B1 | UNKNOWN logic copied with Solana assumptions (blockhash expiry) | H | H | redesign on nonce + tx hash + receipt, fault-injection tests |
| B2 | Accepted tx treated as final (Helicon) | M | H | receipt status plus `finalized`/N blocks gates |
| B3 | Empty `eth_getLogs` near head read as no event | M | H | confirm with receipt or `eth_call` state read |
| B4 | Duplicate submission / double fund | M | H | persist hash before send, idempotency key = milestone + step, contract rejects second fund |
| B5 | Nonce races across instances | M | H | DB-backed allocator with lock, single-writer per signer |
| B6 | Hidden Solana literals leak (status strings, `"solana"` to MISA, explorer URLs) | H | M | neutral `EscrowState.status`, chain id parameter, grep gate in CI |
| B7 | Legacy payout removed or broken | L | H | keep Solana legacy path until SQL evidence |
| B8 | Schema change breaks data | L | H | forward-only additive migrations, tested on a DB copy |

## Security risks
| # | Risk | L | I | Mitigation |
|---|---|---|---|---|
| X1 | Private key exposure | M | H | KMS non-exportable keys, no plaintext keys, startup guards (no local signer on Mainnet chain id) |
| X2 | Unauthorized signing by backend | M | H | signer only reachable by gateway, internal API key, action allowlist, audit log |
| X3 | Wrong chain id | M | H | verify `eth_chainId` at startup and per request, sign with configured chain id (EIP-155) |
| X4 | Malicious/inconsistent RPC | L | H | two providers, cross-check heads, validate emitter address and receipt `to` |
| X5 | DB/on-chain inconsistency | M | H | reconciler compares `eth_call` state to MySQL, evidence records, alerts |
| X6 | Secrets in repo/CI | M | H | secret store, placeholders only, existing `.env` exclusion |

## RPC risks
| # | Risk | L | I | Mitigation |
|---|---|---|---|---|
| R1 | Public RPC limits/outages | H | M | paid primary + fallback |
| R2 | `eth_getLogs` range limits | H | L | chunks of 2,000 blocks or fewer |
| R3 | Provider lag/stale reads | M | M | head cross-check, retry with backoff |
| R4 | Unclear Helicon behavior (e.g. `newHeads`) | M | M | Phase 1 experiments on Fuji; avoid depending on subscriptions |

## Data and operational risks
| # | Risk | L | I | Mitigation |
|---|---|---|---|---|
| D1 | Unknown rail data mix in production | H | H | run ADR SQL evidence before touching legacy paths |
| D2 | In-flight Solana escrows at cutover | M | H | they complete on Solana; both adapters stay available |
| O1 | Users lack AVAX for gas | H | M | UX guidance; evaluate relayer later |
| O2 | Two chains to operate during transition | H | M | feature flags, runbooks, monitoring for both |
| O3 | Fuji test USDC differs from production behavior | M | M | fork tests, minimal Mainnet smoke before launch |
| O4 | Team skill gap in Solidity | M | M | audit, Foundry training, small scope |

## Revision 2 risks (decisions D1-D4)
| # | Risk | L | I | Mitigation |
|---|---|---|---|---|
| X7 | **Single admin key reproduced on EVM** (config admin, arbiter, co-signer, claimer and resolver in one key, as in the Anchor program) | M | H | separate roles and keys ([03](03-target-architecture.md) section 9); backend authorization signer cannot move funds or resolve disputes; admin multisig has no fund access; negative tests in Phase 3 |
| X8 | Backend authorization signer compromised: attacker mints valid `fund`/`submit`/`requestRevision` authorizations | M | H | KMS key, per-escrow nonce, short deadline, parameters bound in the signature, contract range checks still apply, fast rotation by admin, monitoring of authorizations against MySQL records; impact limited because the signer cannot release or refund |
| X9 | Submit authorization abuse starts the review clock (and with it auto-release) for unrecorded evidence | M | H | `SubmitAuthorization` binds `evidenceHash` and `submissionCount`; backend signs only for recorded submissions |
| X10 | Arbiter key lost or compromised | M | H | separate KMS/multisig key, admin rotation, role-based arbiter (open vs snapshot); recovery drill |
| X11 | Shared contract: one USDC blacklist or pause of the contract address freezes every open escrow (Solana's per-escrow vaults avoid this) | L | H | Phase 3 gate on factory/clones; pull or claimable payout design; operational monitoring of the token's blacklist and pause events |
| X12 | Deadline race: a transaction submitted before a deadline executes after it under asynchronous execution | M | M | contract checks execution-block time; backend re-reads escrow state before concluding failure; `block.timestamp` semantics tested in Phase 1 |
| X13 | Contract rules drift from the Anchor semantics (timing boundaries, bounds, no unilateral refund) | M | H | mapping table with one test per row, boundary tests, review against the Rust source |
| X14 | Preserved ambiguities (Revision state with no deadline, no refund after missed delivery, binary dispute outcome, never-expiring extension request) lock funds longer than intended | M | M | explicitly recorded in [07](07-open-questions.md); preserve as is unless approved; document operational procedures |
| X15 | Pause semantics diverge (pause freezing release or refund) | L | H | pause blocks only new funding, as on Solana; invariant tests |
| X16 | Legacy payout paths altered or removed without evidence | L | H | no change without read-only SQL evidence; historical references preserved |

## Rollback
Phases 1-5 are additive: remove the module/folder or turn the flag off. Phase 4 migrations are additive and ignored by Solana paths. Final switch rollback = configuration flip back to the Solana adapter (rehearsed in Phase 6); escrows created on Avalanche continue to be served by the Avalanche adapter until they settle, so rollback affects new escrows only.
