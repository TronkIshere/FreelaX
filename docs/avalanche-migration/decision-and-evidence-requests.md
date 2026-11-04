# Open owner decision requests and operator evidence requests (2026-11-03)

Status register: [PROJECT_STATUS](PROJECT_STATUS.md). This page only asks; it decides nothing. Questions already answered by the
owner response of 2026-11-03 are not repeated.

**Owner decision update (2026-11-03)** recorded in [PROJECT_STATUS](PROJECT_STATUS.md) "Owner decision update":

| Request | Status |
|---|---|
| R1 | **PENDING** (Fuji signer and funding owner; with X3) |
| R2 | **ANSWERED IN PART**: development database only, six read-only queries; DB operator identity and evidence **PENDING** |
| R3, R4, R5 | **PENDING** (E4 limitation or waiver; E5/E6 after E1 and evidence review; E7 optional/pending) |
| R6 | **PENDING**: Phase 3 proposal NOT APPROVED |
| R7 | **ANSWERED**: O-2 shared immutable baseline, clone prototype LOCAL_SIMULATION only; O-6 stuck-and-reported accepted, no privileged withdrawal, bypass or automatic rerouting |
| R8 | **ANSWERED IN PART**: fee, tax/MISA, payout and notification work (A4) moves to Phase 3. Still open: the C3 treatment option; C4 and the placement of A5 |
| R9, R10 | **PENDING** |
| R11 (new 2026-11-04) | **PENDING**: Java 17 module changes for Phase 2 residuals A3 (marketplace half) and A7 |

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
| R10 | Approve the [CI proposal](ci-proposal.md), including pinning forge-std and the CI Node version | quality item D1 | approve / amend | D1 |

## B. Operator evidence requests
| ID | Who | What | Exact instructions | Deliverable |
|---|---|---|---|---|
| X1 (E1) | human operator with the owner-approved signer, after R1 | own Fuji transactions with the lab contract (testnet AVAX only) | [phase-1/prototypes/human-signed/README.md](phase-1/prototypes/human-signed/README.md) "Procedure" | `phase-1/evidence/live/AVALANCHE_FUJI-human-signed-chain43113-<utc>.json`; note the faucet's stated limits |
| X2 (E2) | DB operator, once named (R2 remainder) | counts for the six read-only queries, **development database only** (R2, 2026-11-03) | [phase-1/db-rail-evidence-request.md](phase-1/db-rail-evidence-request.md) "Operator checklist and required output format" | one file, `rail-evidence-development-<yyyymmdd>.md`, under `phase-1/evidence/db/` |
| X3 (B1, later B2) | owner-named funding owner | funding of the E1 address; later the human-signed escrow deployment | `avalanche-contracts/deployments/README.md` (Fuji section, still BLOCKED) | deployment manifest `AVALANCHE_FUJI.json` (addresses only) |
