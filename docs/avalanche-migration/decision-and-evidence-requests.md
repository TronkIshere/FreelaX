# Open owner decision requests and operator evidence requests (2026-11-03)

Status register: [PROJECT_STATUS](PROJECT_STATUS.md). This page only asks; it decides nothing. Questions already answered by the
owner response of 2026-11-03 are not repeated.

**Engineering decisions under delegated authority (2026-11-04, Lead instruction):** R3 (option a), R5 (option a), R9 (EIP-191
challenge verified in the gateway, implemented as A8), R10 (CI proposal accepted, workflow added locally), R11 (option a,
additive Java 17 changes) and the R8 remainder for C4 (marketplace-driven status, A5 closed) were **decided by engineering**,
not by the owner; see PROJECT_STATUS "Delegated decision classification". Still open for the owner or external people: **R1,
R2 remainder (DB operator), R4, R6** (Phase 3 approval), the business part of R8/C3 beyond notifications, and X1 to X3.

**Owner decision update (2026-11-03)** recorded in [PROJECT_STATUS](PROJECT_STATUS.md) "Owner decision update":

| Request | Status |
|---|---|
| R1 | **ANSWERED 2026-11-05**: the owner signs (encrypted keystore, `cast --keystore`, new testnet-only wallet), funds from the faucet and runs X2; X1 to X3 still to be executed |
| R2 | **ANSWERED 2026-11-05**: development database only, six read-only queries; DB operator = the owner; evidence **PENDING** (X2) |
| R3, R4, R5 | R3 and R5 **ENGINEERING DECIDED 2026-11-04** (option a each, see the note above); R4 **RECORDED 2026-11-05 as instructed in session: option (a) documented limitation** (local-scope completion; no Fuji re-run); **pending owner confirmation if the sender was not the owner**; not a waiver |
| R6 | **ANSWERED 2026-11-05: APPROVED as proposed** by the owner (PROJECT_STATUS "Owner decisions (2026-11-05)"). Before: engineering execution under delegated authority, owner approval pending |
| R12 | **ANSWERED 2026-11-05: option (1) adopt** (O-5 question 8 amended for the two-party path; new contract version under O-2). Previous text: **PENDING (owner)**: Lead target design (two-party settlement, no reassignment, no privileged withdrawal) built as an isolated LOCAL variant; adoption conflicts with O-5 question 8 and needs a new contract version (O-2) |
| R7 | **ANSWERED**: O-2 shared immutable baseline, clone prototype LOCAL_SIMULATION only; O-6 stuck-and-reported accepted, no privileged withdrawal, bypass or automatic rerouting |
| R8 | **ANSWERED IN PART**: fee, tax/MISA, payout and notification work (A4) moves to Phase 3. Still open: the C3 treatment option; C4 and the placement of A5 |
| R9, R10 | **ENGINEERING DECIDED 2026-11-04** (see the note above; R9 implemented as A8, R10 workflow added) |
| Payout (new 2026-11-04) | **ANSWERED 2026-11-05**: mock partner only (real partner out of scope); fee and tax reuse the existing Solana-rail rules, labelled simulated. Previous text: **OPEN (owner / external)**: a non-custodial Avalanche payout adapter exists, default off, LOCAL mock partner only ([phase-3/03](phase-3/03-avalanche-payout-adapter.md)). Needed before any real payout: a partner that supports the exact Avalanche USDC token and network, its authorization and credentials; the platform fee (if any) and the tax treatment of an Avalanche payout (no rule was invented) |
| R11 (new 2026-11-04) | **ENGINEERING DECIDED 2026-11-04** (option a; see the note above). Original request: Java 17 module changes for Phase 2 residuals A3 (marketplace half) and A7 |

## A. Owner decision requests
| ID | Decision needed | Why now | Options (recommendation first) | Unblocks |
|---|---|---|---|---|
| R1 (C1) | **Fuji human-operated signing procedure** for Phase 1 E1: who funds which address, and which signing device | E1 is the largest Phase 1 blocker; tooling is ready ([human-signed](phase-1/prototypes/human-signed/README.md)) | (a) hardware wallet via `cast --ledger`; (b) operator-held encrypted keystore via `cast --keystore` on the operator's own machine; (c) another device the owner names | E1 (#14, #18, #25), E5 faucet limits, E6 own-transaction data; later B2 to B4 |
| R2 | Authorized environments for the read-only DB evidence (E2) and who the DB operator is | E2 cannot start without both | **Environment answered 2026-11-03: development database only.** Remaining: name the DB operator (role) | E2, then C8 |
| R3 | E4: accept "no official USDC.e address source exists" as a **documented limitation**, or explicitly waive E4 | official sources checked 2026-11-03 publish no address; code already rejects USDC.e by allow-list and symbol | (a) documented limitation, keep the deny-list entry; (b) explicit waiver | E4 |
| R4 | E5 threshold and E6 residual observations: documented limitation or explicit waiver once the E1 run is done | public endpoints publish no threshold; some Helicon states may stay unobservable | (a) documented limitation after E1; (b) explicit waiver | E5, E6 |
| R5 | E7: is FreelaX escrow explorer verification on Fuji wanted (backlog row 19 made it optional)? May one read-only Fuji `eth_getStorageAt` confirm the current USDC implementation slot? | verification needs B2 and a Snowtrace API key | (a) optional, mark NOT APPLICABLE until B2; (b) required after B2 | E7 |
| R6 (C9) | Approve, change or reject the [Phase 3 proposal](phase-3/00-phase-3-proposal.md) | the owner asked for the proposal before Phase 3 | approve / amend / reject | P3-M0 |
| R7 (C2, C5) | O-2 shared versus clones; O-6 acceptance of stuck-and-reported | both gate any contract change; analysis in the proposal sections 6.2 and 6.3 | **ANSWERED 2026-11-03**: O-2 shared immutable escrow remains the baseline, factory/clone prototype permitted exclusively in LOCAL_SIMULATION; O-6 stuck-and-reported accepted, no privileged withdrawal, bypass or automatic rerouting | P3-M4, O-6 closure |
| R8 (C3, C4) | Fee, tax, payout and notification treatment of an Avalanche release; scanner-driven versus marketplace-driven status; and whether A4 and A5 stay Phase 2 items or move to Phase 3 | they are the only Phase 2 implementation items that cannot start without a decision | **Answered in part 2026-11-03**: fee, tax/MISA, payout and notification work (A4) moves to Phase 3. **Still open:** which C3 treatment (labelled mocks, wired to existing stages, or left off the rail); C4; whether A5 moves | A4 (placement), A5 |
| R9 (new) | EVM wallet linking design for the marketplace (register A8) and how LOCAL "simulated account" execution maps a user to the gateway's ephemeral wallets (F3) | without it a real single-stack run (A2) cannot enable the Avalanche rail | (a) EIP-191/EIP-712 challenge as planned in `phase-2/04` section 3, verified in the gateway (Java 21 has web3j) and called by the marketplace; (b) LOCAL-only seeded EVM addresses for demo users | A2, Phase 3 O1 |
| R11 (new 2026-11-04) | The execution instruction of 2026-11-04 says "do not modify Java 17 legacy modules". The marketplace half of **A3** (fault injection seen through the marketplace: tests only, in `marketplace-backend`) and both parts of **A7** (`"solana"` literal in `SettlementTaxService`, `TaxCertificateServiceImpl`, `misa-backend`; `paymentNetworkId` in the contract DTO for UI labels) live in Java 17 modules. Earlier Phase 2 milestones (M8, M9) did change `marketplace-backend` additively | Without an answer, A3 stays PARTIAL and A7 NOT IMPLEMENTED; nothing else is blocked. Also the same question as R6 package D-A | (a) allow additive changes and test-only additions in `marketplace-backend`, Java level and Solana behaviour unchanged, Solana suites must stay green; (b) test-only additions allowed, production code not; (c) no change: move A7 to Phase 3 with A4 and keep A3 at gateway level | A3, A7 |
| R12 (new 2026-11-04) | F14: a Disputed escrow with a lost arbiter key cannot be settled. Accept as a documented limitation, or approve a contract change (for example allowing mutual refund from Disputed, or an owner-approved arbiter replacement that changes O-3) | the drill found it; any fix touches the immutable baseline or O-3 | **Update 2026-11-04:** the Lead chose the target design (b)-shaped: two-party EIP-712 settlement from Disputed, no reassignment (O-3 kept), no privileged withdrawal. Built and tested as an isolated LOCAL-only variant ([lab](phase-3/prototypes/escrow-mutual-settlement/README.md)). **Owner still decides:** (1) amend O-5 question 8 for this path, (2) accept a new contract version and deployment under O-2; or (a) keep F14 as a documented limitation | F14 |
| R10 | Approve the [CI proposal](ci-proposal.md), including pinning forge-std and the CI Node version | quality item D1 | approve / amend | D1 |

## B. Operator evidence requests
| ID | Who | What | Exact instructions | Deliverable |
|---|---|---|---|---|
| X1 (E1) | human operator with the owner-approved signer, after R1 | own Fuji transactions with the lab contract (testnet AVAX only) | [phase-1/prototypes/human-signed/README.md](phase-1/prototypes/human-signed/README.md) "Procedure" | `phase-1/evidence/live/AVALANCHE_FUJI-human-signed-chain43113-<utc>.json`; note the faucet's stated limits |
| X2 (E2) | DB operator, once named (R2 remainder) | counts for the six read-only queries, **development database only** (R2, 2026-11-03) | [phase-1/db-rail-evidence-request.md](phase-1/db-rail-evidence-request.md) "Operator checklist and required output format" | one file, `rail-evidence-development-<yyyymmdd>.md`, under `phase-1/evidence/db/` |
| X3 (B1, later B2) | owner-named funding owner | funding of the E1 address; later the human-signed escrow deployment | `avalanche-contracts/deployments/README.md` (Fuji section, still BLOCKED) | deployment manifest `AVALANCHE_FUJI.json` (addresses only) |

## R13 (new 2026-11-12): Solana code paths left in the marketplace after the modules left `master`
**Finding (audit, no change made):** 19 classes in `marketplace-backend` still call the Solana gateway
(`SolanaCprClient`, `SolanaGatewayAdapter`, `SolanaEscrowFundingService`, `SolanaEscrowTimeoutService`,
`SolanaEscrowWorkflowService`, `SolanaOnRampProvider`, the unified-rail payment, payout, off-ramp and VND payout services,
`SolanaEscrowTimeoutScheduler`, and related). None is behind a flag; with the Solana gateway gone they fail only when called,
with a connection error. The schedulers poll the database and call the gateway only for existing Solana escrows.
**Options (recommendation first):**
(a) add a `solana.enabled` flag, default **false** on `master`, that makes these paths fail closed with a clear error and keeps
the schedulers idle (additive, reversible, the code stays for reference);
(b) remove the code from `master` (it is preserved on branch `solana`), which touches the unified and partner rails and needs
a careful split;
(c) leave as is (dormant code, failures only on use).
**Who decides:** the owner (behaviour and scope of the legacy rails).
