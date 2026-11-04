# Claude instructions: FreelaX Avalanche migration

Scope of this file: all work on the Solana to Avalanche C-Chain migration (`docs/avalanche-migration/`,
`avalanche-integration/`, `avalanche-contracts/`, and the Avalanche parts of `marketplace-backend/`, `frontend/`,
`misa-backend/`, root build and compose files). Imported from the repository root `CLAUDE.md`.

This file holds **standing rules**. It does not hold status. Status, owner decisions and outstanding items live in one place:
[PROJECT_STATUS.md](PROJECT_STATUS.md). **If this file and PROJECT_STATUS.md disagree, PROJECT_STATUS.md wins**; fix this file.
Execution history and the resume point: [AUTONOMOUS_EXECUTION_LOG.md](AUTONOMOUS_EXECUTION_LOG.md).

## 1. Resuming work (read this first, every session)
1. Read, in order: [AUTONOMOUS_EXECUTION_LOG.md](AUTONOMOUS_EXECUTION_LOG.md) "Current state" and the latest entries;
   [PROJECT_STATUS.md](PROJECT_STATUS.md) (register sections A to G, "Owner decision update", "Next recommended action");
   [decision-and-evidence-requests.md](decision-and-evidence-requests.md) (what is still asked of the owner and operators).
2. Run `git status` and `git log --oneline origin/master..HEAD` to see uncommitted and unpushed work.
3. Do **not** repeat completed investigations. Phase 0 research, Phase 1 experiments, the M1 to M12 implementation and the
   2026-11-03 audit are recorded; reuse their findings by reference. Re-run something only to validate a change or when the
   log says the evidence is stale.
4. Do **not** ask the owner questions already answered (owner response 2026-11-01, M1/M2 instructions, owner response and
   decision update 2026-11-03) or already filed as a request (R1 to R10, X1 to X3). If work is blocked on one, record the
   block in the log and move to the next unblocked item.
5. Pick the next action from the log's "Recommended next action", checked against the register.

## 2. Objective and scope
- Objective (Phase 0, [README](README.md)): make Avalanche C-Chain the primary blockchain integration of FreelaX; Solana code,
  modules, docs and records stay intact as the legacy reference; Avalanche is built and tested independently and integrated
  progressively.
- **Permanent constraint** ([../SIMULATION_SCOPE.md](../SIMULATION_SCOPE.md)): FreelaX is a development simulation. No real
  funds, no real financial settlement. Only **LOCAL_SIMULATION** (Anvil, chain id 31337, simulated USDC) and
  **AVALANCHE_FUJI** (chain id 43113, testnet AVAX and Circle test USDC) exist. **Mainnet (43114) is out of scope**: not
  implemented, configured, deployable, forked or contacted. A local Fuji fork reports 43113 and is still LOCAL_SIMULATION.
- Terminology (scope section 7): "Simulation Balance", "Testnet USDC", "Simulated Escrow", "Testnet Transaction",
  "Demonstration Only". Never imply real money, withdrawable balances or settlement.

## 3. Architecture and technology constraints
- Decisions D1 to D4 ([README](README.md) decision register): C-Chain only; shared immutable Solidity escrow with logical
  per-escrow isolation (D2); role-separated signing, the backend authorization signer can never move funds or override
  disputes (D3); escrow rules on-chain, permissionless eligible release (D4).
- Modules: `avalanche-integration` (separate gateway service, O-1; **Java 21**, web3j 6.1.0, Spring; JDK-activated Maven
  profile, **JDK 17 builds intentionally exclude it**); `avalanche-contracts` (Foundry 1.8.7, solc 0.8.38, OpenZeppelin
  v5.7.0); `marketplace-backend` (Java 17; Avalanche rail behind `avalanche-gateway.enabled=false`); `frontend`
  (React/Vite, EIP-1193 adapter, chain allow-list 31337/43113).
- Legacy modules stay on **Java 17**; no global Java baseline change, no change to `Dockerfile.maven` or Solana targets
  (O-16: root changes additive only). Persisted enums unchanged; schema changes are additive, forward-only Flyway.
- Transaction model: union state set CREATED, SIGNED, SUBMITTED, PENDING, CONFIRMED, FINALIZED, REVERTED, FAILED, REPLACED,
  UNKNOWN (O-10, O-17). Confirmation keys on the receipt and `finalized`, never on submission or empty logs near head.
- Assets by `(chainId, contract address)` from explicit configuration, never by symbol. USDC.e rejected.
- Fuji RPC host allow-list (M1): `api.avax-test.network`, `avalanche-fuji-c-chain-rpc.publicnode.com` only. O-8 (provider
  choice) DEFERRED; a new provider needs a documented proposal and owner approval.

## 4. Confirmed owner decisions (summary; authoritative text in PROJECT_STATUS.md)
- 2026-11-01: D1 to D4; Java 21 for the Avalanche module; web3j 6.1.0; KMS deferred; simulation-only scope; O-1, O-3 to O-5,
  O-7, O-9 to O-12, O-14 to O-18 (see "Approved decisions"). O-13 (external audit, key ceremony) and O-8 DEFERRED.
- M1 review: JDK profile accepted; Fuji host allow-list; single-label hosts LOCAL only.
- **M2 (authoritative, reaffirmed 2026-11-03):** development signers **only in LOCAL_SIMULATION**; no backend private key,
  development signer or signing operation in AVALANCHE_FUJI; ephemeral keys, never persisted, logged or exposed. The Phase 1
  `LiveExperimentsTest` Fuji key-file procedure does **not** override this. No backend Fuji signer as a workaround.
- Owner response 2026-11-03: Phase 3 is a new phase after Phase 2; no blanket waiver (E2, E4 to E7 tracked individually);
  CI is a quality item (D1), not a Phase 1 requirement; DB evidence only by the DB operator, read-only, authorized
  environments only; local tests with compatible toolchains, no global installs.
- **O-2 (DECIDED 2026-11-03):** the **shared immutable escrow remains the baseline**. A factory/clone prototype is permitted
  **exclusively in LOCAL_SIMULATION** (isolated folder outside `avalanche-contracts/src`, never a Fuji manifest or 43113
  script). No prototype has been built; building it is Phase 3 work (P3-M4) and needs R6 approval first.
- **O-6 (DECIDED 2026-11-03):** **stuck-and-reported** escrow behaviour under a USDC blacklist is accepted. **No privileged
  withdrawal, no bypass, no automatic rerouting**, ever. Contract behaviour unchanged.
- **R8 (ANSWERED IN PART 2026-11-03):** the Avalanche fee, tax/MISA, payout and notification work (**A4**) **moves to
  Phase 3**. Still open: the C3 treatment option (labelled mocks, wired to existing stages, or off the rail); C4; placement
  of A5 (stays a Phase 2 item until the owner decides).
- **R2 (ANSWERED IN PART 2026-11-03):** the six read-only queries may run against the **development database only**; the
  DB operator is still unnamed.

## 5. Phase boundaries
- **Phase 1: PARTIAL.** Blocked items E1 (human-signed Fuji run, needs R1/C1 and B1) and E2 (DB evidence, needs the DB
  operator). Nothing is WAIVED. Claude may maintain the keyless tooling and its LOCAL validation; Claude may **not** sign,
  fund, or run anything against Fuji that writes, nor query any database for E2.
- **Phase 2: IMPLEMENTATION COMPLETE WITH EXTERNAL VALIDATION PENDING** (not "accepted", not "fully validated"). Authorized
  remaining work: approved residuals **A1** (frontend action screens), **A3** (combined fault injection at marketplace level),
  **A7** (Solana-literal clean-up in MISA/tax flows and explorer labels), **A6** (optional).
  Since 2026-11-04: A1, A2, A3, A6, A7, A8, F12, F13 IMPLEMENTED and tested; A5 closed (D-C). Phase 2 has no open
  engineering residual; B7 (browser wallet) is external. The single-stack harness is `phase-2/a2-local-stack/run.sh`. **A8** waits for R9 (wallet
  linking design); **A2** waits for A8 and F3; **A5** waits for C4. A4 is no longer Phase 2.
- **Phase 3: PROPOSED, NOT OWNER APPROVED** ([phase-3/00-phase-3-proposal.md](phase-3/00-phase-3-proposal.md), R6/C9 open).
  **Never mark R6 approved.** Since the Lead's delegation of 2026-11-04, engineering may implement Phase 3 items that are
  reversible, simulation-only, default-off (or default to current behaviour) and covered by existing owner decisions; each is
  recorded as ENGINEERING DECIDED UNDER DELEGATED AUTHORITY (PROJECT_STATUS classification table). Done so far: settlement
  notifications (flag, default off), primary-rail policy for new contracts (default SOLANA), stuck-escrow reporting (P3-C4),
  scanner health (A6), CI workflow (not executed), drills. Fee/tax/payout for Avalanche needs an off-ramp (new custody path):
  owner decision, do not build. F14 (arbiter loss on a Disputed escrow) is owner request R12: no contract change. Not covered by the delegation:
  anything irreversible, custody-changing, Fuji signing, real financial settlement.
- Naming trap: "Phase 3" in older documents (README D2, `04`, `07`, Phase 1 reports) means the Phase 0 roadmap's Solidity
  phase, executed inside P2-M5 to M7. The repository milestone names in [phase-2/README.md](phase-2/README.md) win over any
  differently numbered prompt.

## 6. Security and deployment restrictions (hard stops)
Never, without a new explicit owner decision recorded in PROJECT_STATUS.md:
- push, open a PR, or otherwise publish to GitHub (local commits are authorized);
- deploy to Fuji, sign a Fuji transaction, hold or generate a Fuji key, or request a private key, mnemonic or keystore;
- touch Mainnet in any way (RPC, fork, configuration, address);
- read `.env` or any ignored secret file; commit secrets; import production wallets;
- connect to any database other than local Testcontainers/compose instances Claude starts itself;
- change the escrow contract in `avalanche-contracts/src`, the deployment architecture, the blacklist behaviour, the chain or
  host allow-lists, or the Java/Node baselines;
- change, reinterpret or pre-empt an owner decision, mark anything WAIVED or APPROVED, or fabricate operator evidence
  (X1 to X3), DB counts, Fuji results, faucet limits or explorer data;
- perform global installs or toolchain upgrades on the machine;
- change Java 17 modules beyond **additive** changes: R11 (engineering decided under the Lead's delegation, 2026-11-04)
  allows additive code, migrations and tests in `marketplace-backend` (and `misa-backend` only if needed); the Java level,
  Solana behaviour, legacy defaults and existing persisted values stay unchanged, and the full legacy JDK 17 reactor must pass.
Every new configuration flag defaults to current behaviour. The application must keep failing closed on an unsupported
network, Mainnet endpoint or production-looking credential.

## 7. Testing and evidence requirements
- Every code change: run the affected module's suite and record counts (run / pass / skipped) in the log and in the
  relevant report. Report environment incompatibilities separately from code failures.
- Baselines to keep green (2026-11-04, last measured): legacy JDK 17 reactor payment 72, marketplace 414, misa 20,
  solana-integration 10; avalanche-integration 115 (3 optional skipped); Foundry 55 pass, 1 skipped; frontend 29 files,
  867 tests; human-signed tooling 11; A2 harness PASS. A drop is a regression unless
  explained. The opt-in MySQL ITs (`*MySqlIT`, `-Dfreelax.mysql.url`) run against a throwaway local MySQL only.
- This machine (B10): JDK 25 only; Maven 3.10.0 in `~/.m2/wrapper/dists/apache-maven-3.10.0/*/bin` (not on `PATH`); JDK 23+
  needs `-Dmaven.compiler.proc=full` for `payment-backend`. Use pinned images for real runtimes: `maven:3.9-eclipse-temurin-17`
  (legacy reactor), `maven:3.9-eclipse-temurin-21` (`-pl avalanche-integration -am test`, Testcontainers via the Docker
  socket), `ghcr.io/foundry-rs/foundry:v1.8.7` (see `phase-1/scripts/forge.sh`; on Git Bash set `MSYS_NO_PATHCONV=1`).
  Gateway suite on JDK 21 from Git Bash (as run on 2026-11-04): `MSYS_NO_PATHCONV=1 docker run --rm -v //var/run/docker.sock:/var/run/docker.sock
  -v "D:/hackathon/FreelaX:/src" -v "C:/Users/Tuan_Minh/.m2:/root/.m2" -w /src -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal
  maven:3.9-eclipse-temurin-21 mvn -B -ntp -pl avalanche-integration -am test`; check the skipped count: Docker-gated tests
  skip silently when Testcontainers cannot reach Docker. E2 query dry run: `phase-1/scripts/db_query_validate.sh`.
  Memory (this machine, 15 GB host, 8 GB Docker VM): run heavy suites one at a time, detached and capped, for example
  `docker run -d --memory=2560m -e MAVEN_OPTS=-Xmx384m ... mvn -B -ntp test -DargLine=-Xmx1g` writing the log to a mounted file,
  then `docker wait`; uncapped JVMs size their heap from the VM and a background shell can be reaped under host pressure.
  Frontend: Node 24.19.0, `npm ci`, `npx tsc -b`, `npx vitest run` (Node 22.10 fails 13 suites, B8). Docker Desktop may need
  starting.
- Evidence files are committed under `phase-1/evidence/` or `phase-2/evidence/`, labelled LOCAL-SIM or FUJI-OBS; Fuji
  evidence is never mixed with local results. Local results are never production or database evidence.
- Status words are exact: PASS, PARTIAL, BLOCKED, DEFERRED, NOT IMPLEMENTED, NOT VALIDATED, INCONCLUSIVE, OPEN, DECIDED.
  Do not round up. A test that did not run is not a pass.

## 8. Autonomous engineering decision rules
Claude decides on its own, and records the decision in the log, when **all** hold: the work is an authorized item (section 5
or a register item with no owner gate); it stays inside existing decisions and section 6; it is reversible by a local
`git revert`; and it does not change persisted data shape beyond additive migrations, public API contracts between services,
or user-visible financial wording beyond the scope terminology. Examples: test design, internal refactors inside a module,
labelled UI states, fixing an inconsistency between documents, choosing among equivalent libraries already in the lockfile.
Claude stops and files an owner request (in [decision-and-evidence-requests.md](decision-and-evidence-requests.md), with
options and a recommendation first) when the choice is business behaviour, security model, contract design, environment,
provider, data retention, scope, or anything listed as an open C-item or R-item. It does not wait idle: it records the block
and continues with the next unblocked item.
When documented plans and code disagree, verify against the code, record the difference (register or report), and do not
silently "fix" plan text that is historical: add a dated note instead.

## 9. Git and change management
- Work on a branch, never directly on `master`. Current branch: see the log. Conventional commit prefixes as used so far:
  `feat(avalanche):`, `fix(avalanche):`, `test(avalanche):`, `docs(avalanche):`.
- Commit locally after each validated unit of work; one logical change per commit; end messages with the attribution line
  from the session's system reminder. **Do not push.** Do not amend or rewrite pushed history; do not skip hooks.
- Historical documents keep their text: add supersession notes and dated change notes rather than rewriting. Update
  PROJECT_STATUS.md (register row, change note) and the log in the same commit as the change they describe.
- Do not modify vendored code (`avalanche-contracts/lib/`, including its third-party `CLAUDE.md`), `node_modules`, or `target/`.

## 10. Pending owner decisions and external blockers (pointer list; status in PROJECT_STATUS.md)
Owner: **R1/C1** Fuji human signing procedure and funding owner; **R2 remainder** DB operator identity; **R3** E4;
**R4** E5/E6; **R5** E7; **R6/C9** Phase 3 approval (**NOT APPROVED**); **R8 remainder** C3 treatment, C4, A5 placement;
**R9** EVM wallet linking design (A8, A2); **R10** CI proposal (D1); C6/O-8, C7/O-13 DEFERRED; C8, C10, C11 OPEN.
Operators / external: **X1** human-signed Fuji run (after R1); **X2** DB evidence, development database only (after the
operator is named); **X3** funding and later human-signed escrow deployment; B7 browser wallet extension; B9 owner `.env`
for `docker compose config`.
