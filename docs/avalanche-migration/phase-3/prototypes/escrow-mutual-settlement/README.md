# R12 / F14: mutual settlement of a Disputed escrow (LOCAL_SIMULATION-only contract variant)

**Status (2026-11-05): ADOPTED.** Owner decision R12 option (1): the addition below now lives in
`avalanche-contracts/src/FreelaXEscrow.sol` and its tests in `avalanche-contracts/test/MutualSettlement.t.sol`. This folder is
kept as the historical lab record. Original status: IMPLEMENTED AND TESTED AS AN ISOLATED VARIANT. NOT ADOPTED. The baseline
escrow is unchanged.
Engineering execution under the Lead's delegated authority (2026-11-04). No owner decision approves a contract change; R12 stays
open for the owner. No script, no manifest, no Fuji profile: this folder cannot deploy anything.

## Why a variant and not a change to `avalanche-contracts/src`
The Lead's R12 target design asks for a two-party, cryptographically verified settlement path for a Disputed escrow, with no
arbiter reassignment and no privileged withdrawal. Inspection found **three existing explicit rules it collides with**:

| Rule | Source | Conflict |
|---|---|---|
| "Mutual refund is unavailable once Disputed" was **preserved** | owner decision **O-5** (2026-11-01, question 8 of `07-open-questions.md`); comment and check in `FreelaXEscrow.sol` lines 240 and 249; test `test_mutualRefund_expiredNotWhileDisputedAndBoundToThisContract` | **Direct.** A two-party settlement from Disputed is exactly an agreed exit from a dispute, which O-5 kept closed. Not resolvable by engineering. |
| Shared **immutable** escrow is the baseline | owner decision **O-2** (2026-11-03); D2 | A new function cannot be added to a deployed immutable contract; it needs a new contract version and a new deployment (deployment-architecture change). |
| No change to the escrow contract without a recorded owner decision | `CLAUDE.md` section 6 | The Lead's delegation is not an owner decision. |

Rules the design **keeps**: O-3 (arbiter snapshot, never reassigned: the arbiter field is never written), O-5 question 6
(binary outcome: no partial split was added), O-6 (stuck-and-reported: a blacklisted recipient makes the settlement revert, no
reroute), D3 (the backend authorization signer has no part in it), D4 (rules on chain).

So the change is **isolated** here, as the Lead's instruction requires for a conflict, and the conflict is recorded in
PROJECT_STATUS (F14, R12) instead of weakening O-5.

## The design
`FreelaXEscrowMutualSettlement` is a byte-for-byte copy of `FreelaXEscrow` plus one path (the committed evidence file starts
with the full `diff -u` against the baseline):

```solidity
function mutualSettleDispute(bytes32 escrowId, bool releaseToFreelancer, bytes32 settlementHash, uint64 deadline,
                             bytes clientSignature, bytes freelancerSignature) external nonReentrant
```
EIP-712 type (domain `FreelaXEscrow` / `1`, this chain id, this contract):
`MutualSettlement(bytes32 escrowId,bool releaseToFreelancer,uint256 amount,bytes32 settlementHash,uint64 disputedAt,uint64 nonce,uint64 deadline)`

- **Only from `Disputed`**; every other state reverts `InvalidState` (unknown id: `UnknownEscrow`).
- **Both original parties** (the escrow's snapshotted `client` and `freelancer`) must sign the **same** message; the client
  signature first. Arbiter, admin, pauser, the backend authorization signer and strangers count for nothing. One party twice,
  swapped positions, empty, garbage and high-s signatures are refused.
- **Bound to the escrow and the dispute:** escrow id, outcome, full amount, settlement record hash (non-zero, stored in
  `resolutionHash`), `disputedAt` (a pair signed before the dispute is invalid), per-escrow `authNonce` (incremented on use),
  deadline, plus chain id and contract address through the domain. A distinct type hash, so a `MutualRefund` pair is never a
  settlement pair.
- **Replay:** the escrow becomes terminal (`Released` or `Refunded`) and the nonce moves, so a second submission reverts.
- **The arbiter keeps its authority:** while Disputed, whichever valid action lands first settles the escrow; the other then
  reverts on the terminal state.
- **No unilateral withdrawal:** the only destinations are the escrow's own freelancer or client, for the full amount,
  never `msg.sender` (anyone may relay).
- **Events:** `DisputeSettledByParties(escrowId, releasedToFreelancer, settlementHash)` followed by the existing `Released` or
  `Refunded(mutual = true)`. A separate event so an indexer never reads a party settlement as an arbiter `DisputeResolved`.
- **Pause** does not block it (same as release, resolution and mutual refund).

### Transaction states (O-10, O-17)
The ten approved transaction states (CREATED, SIGNED, SUBMITTED, PENDING, CONFIRMED, FINALIZED, REVERTED, FAILED, REPLACED,
UNKNOWN) describe a *transaction*, not an escrow. A settlement is one user-relayed transaction like `mutualRefund`, so it
needs **no new state**: a mined settlement is CONFIRMED then FINALIZED; a signature, state or blacklist failure is REVERTED
with the escrow untouched. The escrow status set (`None` to `Refunded`) is also unchanged: settlement reuses `Released` and
`Refunded`. If the variant were ever adopted, the gateway would need the new ABI entry, the new event in the scanner and the
digest in `EscrowDigests` (not built: the variant is not adopted).

## Tests (Foundry 1.8.7, solc 0.8.38, `via_ir`, MockUSDC)
`./run.sh` copies the baseline suites (Base, Core, Lifecycle, Invariants) into `test/baseline/` (git-ignored) with imports
pointed at the variant, so **every baseline test runs against the variant unchanged**; then runs the new suites.
Committed output: [LOCAL_SIMULATION-mutual-settlement-20261104.txt](LOCAL_SIMULATION-mutual-settlement-20261104.txt):
**72 passed, 0 failed, 0 skipped** = baseline 29 Core + 20 Lifecycle + 1 invariant contract (3 invariants) re-run against the
variant, plus 20 adversarial tests, 1 reentrancy test and 1 invariant contract (3 invariants, settlement in the action mix).

Adversarial coverage: wrong state (each non-Disputed state); duplicate settlement and opposite-outcome flip after settlement;
arbiter first then settlement; wrong parties (one party twice, swapped, arbiter, backend signer, stranger, empty, garbage, admin
as caller); outcome, hash, deadline and amount tampering; disagreeing parties; zero hash; expiry boundary; replay before the
dispute, across escrows, across contracts, across chains (43113) and across message types; high-s malleation; pause;
blacklisted recipient (O-6, stays Disputed, accounting intact); isolation of other escrows; fuzzed signer keys; reentrancy from
the token; accounting, value-conservation and terminal-finality invariants.

Not covered: EIP-1271 contract wallets (F4 applies), any Fuji run, gas comparison against the baseline beyond the forge output.

## What adoption would need (owner, R12)
1. An owner decision that changes O-5 question 8 for this path (and accepts the new contract version under O-2).
2. A new deployment (the baseline is immutable), its manifest, role grants and the gateway/scanner/marketplace wiring above.
3. Review (O-13 is deferred: this is not an audit).
