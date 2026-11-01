# Avalanche migration: project status

Last updated: 2026-11-01. Scope: [SIMULATION_SCOPE](../SIMULATION_SCOPE.md) (LOCAL_SIMULATION and AVALANCHE_FUJI only; Mainnet out of scope; no real funds).

## Current phase and milestone
- Phase 2. **P2-M1 ACCEPTED by the owner (2026-11-01)** with the final decisions below. Report: [phase-2/M1-validation-report.md](phase-2/M1-validation-report.md).
- **P2-M2 (development signers) implemented and validated; WAITING FOR OWNER REVIEW.** Report: [phase-2/M2-validation-report.md](phase-2/M2-validation-report.md). M3 and later have not started. Execution is sequential with owner review after each milestone: M1 > M2 > M3 > M4 > M5 > M6 > M7 > M8 > M9 > M10 > M11 > M12.

## Milestones
| Milestone | Status |
|---|---|
| Phase 0 (research, architecture) | complete |
| Phase 1 (environment, experiments) | **PARTIAL** (funded Fuji wallet and read-only DB evidence still blocked; not waived) |
| P2-M0 | complete (owner decisions recorded below) |
| P2-M1 | **accepted by owner** (validated; 24 tests, 22 pass, 2 optional skipped) |
| P2-M2 | validated, awaiting owner review (43 module tests: 41 pass, 2 optional skipped) |
| P2-M3 to P2-M12 | not started |

## Commits (origin/master)
`3b12e7a` Phase 0, `1662a80` Phase 0 decisions, `e5127ae` Phase 1 checkpoint, `73b5d14` simulation scope, `cf61c0f` Phase 1 Part B (HEAD, pushed).
Uncommitted (nothing committed in Phase 2): `docs/avalanche-migration/phase-2/`, this file, `avalanche-integration/`, `Dockerfile.avalanche`, and additive edits to `pom.xml`, `docker-compose.yml`, `.env.example`.

## Approved decisions (owner, 2026-11-01)
Earlier: D1 to D4; Java 21 baseline for the Avalanche integration; web3j 6.1.0; KMS deferred with ephemeral development signers; simulation-only scope.
M0 decisions:
| ID | Decision |
|---|---|
| O-1 | separate Avalanche integration service |
| O-2 | DEFERRED to the M5 exit (measured gas and security evidence); keep factory/clones possible |
| O-3 | per-escrow arbiter snapshot only; no reassignment, no implicit global rotation |
| O-4, O-5 | preserve verified existing behaviour; invent no rules |
| O-6 | evaluate USDC blacklist behaviour on a Fuji fork at M6; no rerouting or privileged withdrawal; report stuck-escrow behaviour before final acceptance |
| O-7 | users pay testnet gas; relayer limited to release |
| O-8 | DEFERRED (Fuji RPC provider) |
| O-9 | chain identifier added to the existing rail model, backward-compatible migration |
| O-10, O-17 | union state set: CREATED, SIGNED, SUBMITTED, PENDING, CONFIRMED, FINALIZED, REVERTED, FAILED, REPLACED, UNKNOWN (FAILED = definitive application-level failure without on-chain execution; REVERTED = mined and failed; REPLACED = same nonce replaced; UNKNOWN = needs reconciliation); the neutral port may expose a documented mapped subset |
| O-11 | backend development wallets only in LOCAL_SIMULATION, never in AVALANCHE_FUJI |
| O-12 | no WalletConnect in this phase |
| O-13 | DEFERRED: no external audit or key ceremony in this phase (not a production-readiness claim) |
| O-14 | Avalanche for new work; in-flight Solana escrows finish on Solana |
| O-15 | fiat operations remain explicitly labelled mocks |
| O-16 | additive-only root changes (module in reactor, compose service, `.env.example` placeholders, `avalanche_gateway` in `init-db.sql`, dedicated JDK 21 Dockerfile); no change to the Java 17 baseline, Solana targets or `Dockerfile.maven` |
| O-18 | Testcontainers for integration tests; runtime uses a separately provisioned local MySQL database `avalanche_gateway`; no `.env` reads, no unidentified database |
Fuji authorization: paced read-only `eth_chainId` and `web3_clientVersion` only; Fuji tests optional and never required by the default build.

### M1 review decisions (owner, 2026-11-01)
1. The JDK-activated Maven profile is accepted. JDK 21 validation is mandatory for the Avalanche module; **JDK 17 builds intentionally exclude Avalanche.**
2. Fuji RPC host allow-list accepted: `api.avax-test.network` and `avalanche-fuji-c-chain-rpc.publicnode.com`. Additional providers require explicit review.
3. Single-label hostnames such as `anvil` are accepted only in LOCAL_SIMULATION and never bypass Fuji hostname validation (verified in code and by tests).
4. `CREATE DATABASE avalanche_gateway` in `init-db.sql` is an explicitly **DEFERRED O-16 item, scheduled for M3, not completed.**

### M2 owner instructions (2026-11-01)
Development signers are permitted **only in LOCAL_SIMULATION**; no backend development signer, private key or signing operation in AVALANCHE_FUJI; no Mainnet; no KMS; ephemeral keys, no silent persistence, never logged or exposed. This **supersedes** the Phase 2 plan text that allowed ephemeral Fuji signers with a funds cap (plan files not edited).

## Outstanding owner decisions
O-2, O-8, O-13 (deferred by owner). Open questions 4 to 8 etc. are covered by O-4/O-5 ("preserve").
**New, needed before M7 and the Fuji parts of M3/M12:** how Fuji deployment and Fuji smoke transactions are signed when the backend may hold no signer in AVALANCHE_FUJI (see the M2 report, section 5). Not decided; no policy invented.

## Deferred items and required follow-ups
- **Deferred O-16 item:** `init-db.sql` `avalanche_gateway` database, to M3 (with O-18).
- **JDK 21 CI validation: none exists** (the repository has no CI configuration). Required follow-up: one JDK 21 step running `mvn -B -pl avalanche-integration -am test` and the Solana baseline commands when a pipeline exists. No CI overhaul proposed.

## Known blockers
- Funded Fuji wallet (human): blocks Fuji deployment, Fuji smoke, Avalanche-native calibration.
- Read-only database evidence (operator): blocks any legacy rail change.
- `JAVA_HOME` is JDK 20 on the development machine: Maven needs JDK 21 for the new module (per-process override used; `mvn` is not on `PATH`).

## Validation results
- M1: Solana baseline unchanged (16/16 tests before and after); `avalanche-integration` 24 tests (22 pass, 2 optional skipped; the owner review added 2 guard tests); real Anvil and Fuji read-only runs pass; Phase 1 Jackson failure reproduced in the real service and fixed by `jackson-annotations:2.22` (module only); packaged jar and a JDK 21 container validated. Details in the M1 report.
- M2: `avalanche-integration` default build 43 run, 41 pass, 0 fail, 2 optional skipped; Anvil test 1/1; packaged jar with signers on real Anvil works and persists nothing; Fuji with signers enabled aborts with `SIGNER_FORBIDDEN_ENVIRONMENT` without any RPC call; Solana baseline unchanged (6/6 and 10/10), JDK 17 compile of existing modules passes. Details in the M2 report.
- Phase 1 evidence otherwise as in [phase-1](phase-1/README.md).

## Next recommended action
Owner reviews M2 and answers the Fuji-signing question when it becomes relevant (before M7). Then M3 (chain client, nonce allocator, transaction tracker) after explicit approval.
