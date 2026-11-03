# CI proposal: JDK 17 legacy modules, JDK 21 Avalanche module, Docker-backed tests

> **Status: PROPOSAL, NOT APPROVED.** Owner response 2026-11-03 section 6: CI stays an engineering quality item (register D1),
> **not** a Phase 1 closure requirement. No workflow file exists or is created by this document. Constraints honoured: the
> Avalanche module is built and tested on JDK 21; the existing Java 17 modules stay on Java 17; the relevant Solana regression
> runs; the global Java baseline (`java.version` 17 in the root `pom.xml`) is not changed; no module is moved to Java 21.

## 1. Why the split is needed (verified 2026-11-03)
- The root `pom.xml` adds `avalanche-integration` only through the profile activated by `<jdk>[21,)</jdk>`. A JDK 17 job builds
  the four legacy modules; a JDK 21 job builds all five (legacy modules still compile with `release 17`).
- `avalanche-integration` tests need Docker (Testcontainers: Anvil 1.8.7 and MySQL); eight classes are gated on Docker or on
  optional environment variables (the Anvil and Fuji smoke tests stay off in CI: no Fuji contact).
- Local baseline reproduced on 2026-11-03 with exactly these containers: `maven:3.9-eclipse-temurin-17` legacy reactor
  (payment 72, marketplace 381, misa 20, solana-integration 10, all pass) and `maven:3.9-eclipse-temurin-21` for
  `-pl avalanche-integration -am` (see PROJECT_STATUS "Validation results").

## 2. Proposed jobs (GitHub Actions, `ubuntu-latest`, which provides Docker)
| Job | Toolchain | Command | Purpose |
|---|---|---|---|
| `legacy-jdk17` | Temurin 17 (`actions/setup-java`, distribution `temurin`, version 17) | `mvn -B -ntp test` | legacy modules on their own Java level, includes the Solana gateway suite and the marketplace suite (Solana regression) |
| `avalanche-jdk21` | Temurin 21 | `mvn -B -ntp -pl avalanche-integration -am test` | Avalanche gateway on JDK 21 with Testcontainers; real web3j calls (Phase 1 requirement: a runtime smoke test, not only a build) |
| `reactor-jdk21` (optional) | Temurin 21 | `mvn -B -ntp test` | proves the full JDK 21 reactor (as in M12) |
| `contracts` | Docker image `ghcr.io/foundry-rs/foundry:v1.8.7` | `scripts/install-deps.sh`, then `forge test` | Foundry unit, fuzz, invariants (the fork suite skips without `FUJI_RPC`; CI never sets it) |
| `contracts-static` | `python:3.12-slim` + `slither-analyzer==0.11.6`, `solc` 0.8.38 | as in `phase-2/evidence/slither-escrow-2026-11-03.txt` | fails on any new detector result not in the M6 triage |
| `frontend` | Node pinned in the job (24.19 verified locally on 2026-11-03; 22.10 is known to fail 13 suites) | `npm ci`, `npx tsc -b`, `npx vitest run` | frontend, including the Solana wallet suites |
| `hygiene` | shell | grep for private-key, mnemonic and PEM patterns, and for Mainnet hosts or chain id 43114 outside the deny-list files | secret and Mainnet guard (scope document section 8) |

## 3. Known issues the pipeline must not hide
1. `forge-std` is cloned without a pin by `avalanche-contracts/scripts/install-deps.sh` (OpenZeppelin is pinned to `v5.7.0`).
   CI should pin forge-std to a recorded commit; doing so changes a project script and needs approval.
2. On JDK 23 and later, `payment-backend` (the only module without an explicit Lombok `annotationProcessorPaths` entry) fails
   to compile unless `-Dmaven.compiler.proc=full` is passed. CI on 17 and 21 is unaffected; developer machines on JDK 25 need
   the flag. Fixing the pom is a separate, approvable change.
3. `docker compose config` needs the owner's ignored `.env`; CI would use a generated placeholder file, never real values.

## 4. Decisions needed
Approve the job list; choose the CI host (GitHub Actions assumed because the remote is on GitHub); approve pinning forge-std
and the Node version used by CI.
