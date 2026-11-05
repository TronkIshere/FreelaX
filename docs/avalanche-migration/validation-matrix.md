# Avalanche migration: validation matrix

Created 2026-11-05. Status of the tests per feature. Status words: NOT PREPARED, PREPARED, IMPLEMENTED, EXECUTED PASS, EXECUTED
FAIL, BLOCKED, DEFERRED. "IMPLEMENTED" means the test code exists; only "EXECUTED PASS" with a date means it was run. Evidence
class: LOCAL-SIM (Anvil 31337 or unit/Testcontainers), FUJI-OBS (owner-run on Fuji), NONE. Results live in
[PROJECT_STATUS](PROJECT_STATUS.md) "Validation results"; this page does not repeat counts it did not measure.

| Feature | Module | Positive / negative cases | Test location | Command | Status | Evidence |
|---|---|---|---|---|---|---|
| Escrow lifecycle, fund, submit, release, refund | contracts | lifecycle, invariants, roles | `avalanche-contracts/test/FreelaXEscrow*.t.sol` | `phase-1/scripts/forge.sh test` (Foundry 1.8.7 image) | EXECUTED PASS 2026-11-05 (77 passed, 1 skipped) | LOCAL-SIM |
| Mutual settlement RELEASE and REFUND, signatures, replay, wrong signer, not Disputed | contracts | 22 settlement tests | `avalanche-contracts/test/MutualSettlement.t.sol` | same | EXECUTED PASS (same run) | LOCAL-SIM |
| Gateway API, typed data, scanner, tracker, fault injection | avalanche-integration | incl. Anvil settlement, swapped signature | `avalanche-integration/src/test` | `maven:3.9-eclipse-temurin-21 mvn -pl avalanche-integration -am test` | EXECUTED PASS 2026-11-05 (119 run, 3 skipped) | LOCAL-SIM |
| Dispute recording on the Avalanche rail (funding proof = applied `fund` row; refused without it; legacy rails unchanged) | marketplace | 5 cases | `ContractDisputeServiceTest` | `mvn -pl marketplace-backend test -Dtest=ContractDisputeServiceTest` | EXECUTED PASS 2026-11-05 | LOCAL-SIM |
| Legacy funding paths refuse an Avalanche-labelled contract | marketplace | fiat covered; Solana-escrow and partner-escrow guards use the same helper | `FundingServiceTest` | `-Dtest=FundingServiceTest` | fiat: EXECUTED PASS; Solana and partner services: **NOT PREPARED** (no unit test class exists for them) | LOCAL-SIM |
| Administrator decision on an Avalanche dispute (needs anchored dispute, moves no money) | marketplace | refused before anchor, accepted after, no payment call | `ContractDisputeServiceTest` | same | EXECUTED PASS 2026-11-05 | LOCAL-SIM |
| `dispute` anchored only when the chain reads Disputed; arbiter `resolve` closes the dispute | marketplace | mismatch, then applied, then RESOLVED_REFUND | `AvalancheEscrowServiceTest` | `-Dtest=AvalancheEscrowServiceTest` | EXECUTED PASS 2026-11-05 | LOCAL-SIM |
| Live admin decision plus arbiter `resolve` on the single stack | A2 harness | needs an administrator account in the harness | `phase-2/a2-local-stack/drive.mjs` | `run.sh` | **NOT PREPARED** (unit level only) | none |
| Single stack: wallet link, fund, submit, release, payout, mutual refund, stuck escrow, R12 settlement RELEASE and REFUND with negatives | A2 harness | see its README | `phase-2/a2-local-stack` | `bash .../run.sh "D:/..../evidence.json"` | EXECUTED PASS 2026-11-05 (re-run after the lifecycle patches) | LOCAL-SIM |
| Wallet adapter, chain allow-list, action screens, settlement UI | frontend | 30 files | `frontend/src/*.test.*` | `npx vitest run` on Node 24.19.0 | EXECUTED PASS 2026-11-05 (30 files, 877 tests, Node 24.19.0) | LOCAL-SIM |
| Real browser wallet | frontend | MetaMask on Fuji or Anvil | none | manual by the owner | BLOCKED (B7) | none |
| Human-signed Fuji tooling | phase-1 | 13 network-free tests | `phase-1/prototypes/human-signed/*.test.mjs` | `node --test` | EXECUTED PASS 2026-11-05 | LOCAL-SIM |
| Fuji escrow deployment, calibration, deadline test on the escrow | contracts | none yet | none | owner signs | BLOCKED (B2, X3) | none |
| Legacy database evidence | phase-1 | six read-only queries | `phase-1/db-rail-evidence-request.md` | owner runs X2 | BLOCKED | none |
