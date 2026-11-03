# 08. Risk and rollback plan (plan only)

Extends [05 risk register](../05-risk-register.md) (including its simulation-scope additions M1-M6). "Production" means the demonstration environment.

## 1. Critical risks
| # | Risk | L | I | Mitigation | Owner of residual risk |
|---|---|---|---|---|---|
| R1 | web3j 6.1.0 + Jackson 3 runtime failure inside a real context | M | H | M1 acceptance requires a real call through the web server; keep web3j isolated in `avalanche-integration`; CI smoke test on every Boot/web3j bump | tech |
| R2 | Mainnet reachable by configuration, fallback or pasted address | L | H | pre-network endpoint check, compiled chain-id allow-list, deny-list addresses, deploy-script chain guard, no environment default | tech |
| R3 | Local fork mistaken for Fuji (reports 43113) | M | M | explicit environment, endpoint class, client-version family as supporting evidence, `environment` on every record | tech |
| R4 | Shared escrow contract frozen by a USDC blacklist; recipient blacklisted | M | H (simulation: M) | isolation gate O-2, pull-payout options evaluated on a fork, invariants; **owner decisions 2026-11-03**: O-2 shared baseline kept (clone prototype LOCAL_SIMULATION only), O-6 stuck-and-reported accepted (no privileged withdrawal, bypass or automatic rerouting) | owner (O-2, O-6: decided) |
| R5 | Business semantics invented by developers | M | H | section 8 of [02](02-contract-implementation-plan.md): unimplemented until answered; "preserve as is" only by owner statement | owner |
| R6 | Duplicate or lost operations (timeouts, restarts, replacement) | M | H | operation-key idempotency, raw tx persisted before send, nonce DB allocator, UNKNOWN never a failure, reconciliation | tech |
| R7 | Public RPC throttling (429) and 100-block log limit slow reconciliation or break clients | H | M | status-before-JSON, `Retry-After`, per-host pacing, provider profiles, second provider of the same environment | tech/owner (provider) |
| R8 | Blocked Fuji validation (funded wallet) leaves Avalanche-native timings, bump threshold and near-deadline inclusion unmeasured | H | M | Anvil path complete first; conservative defaults; runbook ready; Fuji gate stays open | owner (human action) |
| R9 | Legacy rail data unknown; accidental removal of a Solana payout path | M | H | no legacy change without DB evidence and owner approval; additive migrations only | owner |
| R10 | Misleading wording implies real money or withdrawable balance | M | H | terminology rules, labelling, review in M11 | tech/owner |
| R11 | Dev signer compromise or reuse of a real key | L | M | per-role ephemeral keys, no persistence by default, no key import; development signers only in LOCAL_SIMULATION (owner rule M2; the earlier "funds cap" for Fuji signers is superseded because no Fuji backend signer exists) | tech |
| R12 | Java 21 module in a Java 17 reactor (JDK/`release` mismatch) | M | M | build with JDK 21, per-module `release`, validate in M1 | tech |
| R13 | Scope creep into production-grade features (KMS, audits, Mainnet) | M | M | scope document; explicit owner decisions needed | owner |

## 2. Rollback per milestone
| Milestone | Rollback |
|---|---|
| M1-M4 | remove the module entry and folder, drop the `avalanche_gateway` schema; nothing else references them |
| M5-M6 | discard `avalanche-contracts/`; Fuji deployments are disposable and marked superseded in the manifest |
| M7 | redeploy; supersede the manifest; no state to migrate |
| M8 | disable/remove the adapter bean and flag; port additions are additive and unused |
| M9 | flag off; the Flyway migration is additive and ignorable; legacy rows untouched; if necessary restore from the database copy used for migration testing |
| M10-M11 | feature flag off; adapters additive; text changes revertible |
| M12 | nothing to roll back (validation only) |
Global stop rule: a milestone that would require touching Solana modules beyond neutral-port wiring, changing persisted enums, deleting a legacy path, contacting Mainnet or using real funds **stops** and is reported to the owner.
