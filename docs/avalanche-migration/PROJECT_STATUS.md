# Avalanche migration: project status

Last updated: 2026-11-01 (after M3). Scope: [SIMULATION_SCOPE](../SIMULATION_SCOPE.md) (LOCAL_SIMULATION and AVALANCHE_FUJI only; Mainnet out of scope; no real funds).

## Current phase and milestone
- Phase 2. **P2-M1 ACCEPTED by the owner (2026-11-01)** with the final decisions below. Report: [phase-2/M1-validation-report.md](phase-2/M1-validation-report.md).
- **P2-M2 (development signers)** implemented and validated; the owner authorized committing it and starting M3 (2026-11-01). Report: [phase-2/M2-validation-report.md](phase-2/M2-validation-report.md).
- **P2-M3 (chain client, nonce allocator, transaction tracker)** implemented, validated and committed. Report: [phase-2/M3-validation-report.md](phase-2/M3-validation-report.md).
- **Owner authorization (2026-11-01): continue sequentially through M12 without waiting after each milestone**, within the unchanged security and simulation scope. **P2-M4** (log scanner, cursor, provider profiles, WebSocket hints, second-provider cross-check) is validated and committed; report: [phase-2/M4-validation-report.md](phase-2/M4-validation-report.md).
- **Roadmap note:** the autonomous-execution prompt names milestones differently (for example M4 as "transaction lifecycle", M5 as the whole contract system, M7 as business integration, M10 as Fuji validation, M11 as coexistence) and lists phase-2 files that do not exist. The repository roadmap in [phase-2/README.md](phase-2/README.md) is followed; the prompt's themes are covered inside the matching repository milestones and in the M12 acceptance report.

## Milestones
| Milestone | Status |
|---|---|
| Phase 0 (research, architecture) | complete |
| Phase 1 (environment, experiments) | **PARTIAL** (funded Fuji wallet and read-only DB evidence still blocked; not waived) |
| P2-M0 | complete (owner decisions recorded below) |
| P2-M1 | **accepted by owner** (validated; 24 tests, 22 pass, 2 optional skipped) |
| P2-M2 | validated and committed (43 module tests at the time: 41 pass, 2 optional skipped) |
| P2-M3 | **validated and committed, awaiting owner review** (71 module tests: 69 pass, 2 optional skipped) |
| P2-M4 | **validated and committed** (log scanner, cursor, provider profiles, hints, second-provider cross-check; 85 module tests: 83 pass, 2 optional skipped) |
| P2-M5 | **validated and committed** (Foundry project, MockUSDC, escrow core: 29 Foundry tests pass; O-2 still deferred, no gas comparison claimed) |
| P2-M6 to P2-M12 | not started |

## Commits (origin/master)
`3b12e7a` Phase 0, `1662a80` Phase 0 decisions, `e5127ae` Phase 1 checkpoint, `73b5d14` simulation scope, `cf61c0f` Phase 1 Part B (pushed to origin).
Local, **not pushed** (the owner has not authorized a push for these): `0224c71` docs(avalanche): add phase 2 implementation plan; `c8d18cd` feat(avalanche): implement integration foundation and local signers (M1 + M2); the M3 commit is the next commit on `master` after `c8d18cd` (see `git log`).

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
**Needed before M7 and the Fuji parts of M12 (M3 needed no Fuji signing):** how Fuji deployment and Fuji smoke transactions are signed when the backend may hold no signer in AVALANCHE_FUJI (see the M2 report, section 5). Not decided; no policy invented.

## Deferred items and required follow-ups
- **Deferred O-16 item: COMPLETED in M3.** `CREATE DATABASE IF NOT EXISTS avalanche_gateway` was added to `init-db.sql` (one additive line, fresh volumes only). O-18 is implemented: Testcontainers for tests, a separately provisioned local MySQL database for runtime, guarded by `DbEndpointGuard` (local host and the name `avalanche_gateway` only).
- **JDK 21 CI validation: none exists** (the repository has no CI configuration). Required follow-up: one JDK 21 step running `mvn -B -pl avalanche-integration -am test` and the Solana baseline commands when a pipeline exists. It also needs a Docker-capable runner for the Testcontainers tests (M3). No CI overhaul proposed.

## Known blockers
- Funded Fuji wallet (human): blocks Fuji deployment, Fuji smoke, Avalanche-native calibration.
- Read-only database evidence (operator): blocks any legacy rail change.
- `JAVA_HOME` is JDK 20 on the development machine: Maven needs JDK 21 for the new module (per-process override used; `mvn` is not on `PATH`).

## Validation results
- M1: Solana baseline unchanged (16/16 tests before and after); `avalanche-integration` 24 tests (22 pass, 2 optional skipped; the owner review added 2 guard tests); real Anvil and Fuji read-only runs pass; Phase 1 Jackson failure reproduced in the real service and fixed by `jackson-annotations:2.22` (module only); packaged jar and a JDK 21 container validated. Details in the M1 report.
- M2: `avalanche-integration` default build 43 run, 41 pass, 0 fail, 2 optional skipped; Anvil test 1/1; packaged jar with signers on real Anvil works and persists nothing; Fuji with signers enabled aborts with `SIGNER_FORBIDDEN_ENVIRONMENT` without any RPC call; Solana baseline unchanged (6/6 and 10/10), JDK 17 compile of existing modules passes. Details in the M2 report.
- M3: `avalanche-integration` default build 71 run, 69 pass, 0 fail, 2 optional skipped (M3 added 28); Phase 1 scenarios S1-S6 and S8 reproduced on a real Anvil and a real MySQL via Testcontainers (S7 hint part belongs to M4); concurrency (16 threads, 50 submissions) never reused a nonce; Solana baseline unchanged (16/16 combined), JDK 17 reactor and existing modules unchanged; JDK 21 image ran with signers, tracking and a throwaway MySQL, Flyway migrated, wrong database name and missing settings refused. Details in the M3 report.
- Phase 1 evidence otherwise as in [phase-1](phase-1/README.md).

## Known limitations carried forward
Fuji calibration and the finalized-tag path unmeasured (no funded wallet; no backend Fuji signer by owner rule); no second-provider cross-check before REPLACED (M4); no operator procedure to clear a nonce gap; no business authorization or target allow-list in `SubmissionService` (M7/M8/M9); Testcontainers skip-without-Docker behaviour unverified. See the M3 report, section 7.

## Next recommended action
Owner reviews M3. Then M4 (log scanner, cursor, reconciliation, provider profiles) after explicit approval. The Fuji-signing question must be answered before M7.
