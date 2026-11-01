# P2-M6 validation report: contract completion, security review and the Java/Solidity interface

Date: 2026-11-01/02. Scope source: [02 contract plan, P2-M6](02-contract-implementation-plan.md) plus the prompt's M6 themes (ABI integration, EIP-712 parity, error and result mapping), which the repository roadmap places partly in M6 and partly in M7/M8. Owner decisions applied: O-3, O-4/O-5 (preserve), O-6 (evaluate blacklist behaviour on a Fuji fork; no rerouting, no privileged withdrawal; report the stuck-escrow behaviour), O-2 still deferred.
**No deployment to Fuji; no Fuji transaction or signature; no Mainnet contact; no real funds.** Fuji was used only through a read-only fork (`vm.createSelectFork`) of the public RPC; every state change stayed on the fork.

## 1. Contract completion (`FreelaXEscrow`)
| Operation | Rule implemented (Anchor semantics) | Evidence |
|---|---|---|
| `requestRevision` | client; non-zero feedback hash; Submitted; `now < reviewDueAt` (exclusive); `revisionsUsed < maxRevisions`; backend authorization binds feedback hash, counter, nonce, deadline (kept for parity, owner decision O-4); clears the review deadline | cycle test, boundary at `reviewDueAt - 1` and `reviewDueAt`, exhaustion, `maxRevisions = 0`, wrong feedback, stale nonce, wrong counter, non-client |
| `openDispute` | client or freelancer; non-zero reason; Submitted only before `reviewDueAt`; Funded only after `deliveryDueAt` (`<=` refused, `+1` allowed); Revision at any time; clears the review deadline so permissionless release cannot fire | all boundaries, strangers and the arbiter refused, release impossible after a dispute |
| `resolveDispute` | **only the escrow's own snapshotted arbiter**; Disputed only; non-zero resolution hash; binary outcome (full release or full refund) | five other accounts refused, double resolution refused, snapshot survives a default-arbiter change (the new arbiter cannot resolve an old escrow) |
| `mutualRefund` | both parties sign one EIP-712 `MutualRefund` (escrow id, nonce, deadline); anyone may submit; Funded, Submitted or Revision, never Disputed; full refund to the client (the Solana "both must agree" rule; two signatures follow the owner's "preserve" answer on question 10) | swapped, duplicated, foreign and deadline-mismatched signatures refused; expired; bound to this contract; blacklisted client keeps the escrow open |
| Pause | still blocks only `fund`; dispute and resolution keep working while paused | `test_resolve_pauseDoesNotBlockDisputeOrResolution` |
Unchanged from M5: no unilateral refund exists, no role can reach escrowed funds, the arbiter is never reassigned.

## 2. Test results
- **Foundry:** 49 passed, 0 failed, 1 skipped (the fork suite's `setUp` skips without `FUJI_RPC`): 29 core, 19 lifecycle, and the stateful invariant suite (3 invariants, 64 runs of 64 calls by default; also run once at 300 runs of 100 calls: pass). The invariants: the contract's token balance equals the sum of its open escrows; value is conserved (so no escrow can ever pay out more than its deposit); a settled escrow never changes status again. Coverage was checked, not assumed: with the state-building actions the runs reach released, refunded, disputed and revised escrows.
- **Fork test against the real Circle Fuji USDC** (`UsdcForkTest`, run once with `FUJI_RPC` on the public endpoint; 4 of 4 passed): see section 4.
- **Gateway (Java 21):** 89 run, 87 passed, 0 failed, 2 optional skipped. New in M6: `EscrowContractTest` (4 tests) on the real compiled contract and a real Anvil and MySQL.
- **Solana baseline:** marketplace 6/6 plus gateway 10/10 = 16/16, unchanged.

## 3. Java and Solidity interface
Delivered in `avalanche-integration/.../contract/`: `EscrowAuthorizations` (typed data for fund, submit, revision, mutual refund; chain id and verifying contract in the domain), `EscrowCalls` (calldata), `EscrowEvents` (topic0 computed from the signatures, scanner registration, field decoding), `EscrowErrors` (selector to error name), `EscrowReader` (decodes `getEscrow`), `EscrowReconciler` (scanned events versus on-chain status: the contract wins, the result is a report), `ChainClient.call` with and without a `from` (revert data returned, never thrown). The ABI is exported reproducibly (`avalanche-contracts/scripts/export-artifacts.sh`); the ABI is committed, the creation bytecode is a test fixture.
Proven by `EscrowContractTest` against the real contract:
- Java and Solidity compute **identical digests** for all four authorizations; a digest for another chain id or another verifying contract differs, and the contract refuses it (`InvalidAuthorization`, decoded by the gateway).
- Java event signatures equal the ABI's; every error in the ABI is decodable (only the OpenZeppelin `InvalidShortString()` and `StringTooLong(string)` are tolerated as unreachable).
- A full lifecycle driven through `SubmissionService` and the tracker: fund (Java-signed authorization, buyer wallet), submit, revision, resubmit, a relayer's too-early release mined as REVERTED and mapped to `TooEarly`, the client's release, a dispute with an arbiter refund (a non-arbiter's resolution REVERTED), and a mutual refund signed by both parties' development wallets and submitted by the relayer.
- The scanner decodes the events (Funded, WorkSubmitted, RevisionRequested, DisputeOpened, DisputeResolved, Released, Refunded) and the reconciler reports MATCH for all three escrows; deleting an event makes it report a mismatch while the on-chain status wins.

## 4. O-6: USDC blacklist and issuer behaviour (report before final acceptance)
Both the mock (always) and the real Circle USDC on a Fuji fork (impersonating the issuer's `blacklister` and `pauser` roles on the fork only) show the same behaviour under the approved push-payout baseline, with no rerouting and no privileged path:
| Case | Behaviour |
|---|---|
| Recipient (freelancer) blacklisted at release | `release` reverts; the escrow stays Submitted, funds stay held, accounting unchanged; after unblacklisting, release succeeds |
| Recipient blacklisted at a dispute resolution to the freelancer | `resolveDispute` reverts; the escrow stays Disputed; **the arbiter can still resolve in favour of the client** (a refund works), and an eventual unblacklisting restores the freelancer path |
| Client blacklisted at funding | `fund` reverts; no escrow is left behind |
| Client blacklisted at mutual refund | reverts; the escrow stays open |
| **The shared escrow contract address blacklisted (real USDC)** | every open escrow's release reverts and new funding reverts; all stay in their state; unblacklisting the contract restores all of them. This is the blast radius the O-2 decision weighs: one issuer action freezes every open escrow in a shared contract, whereas per-escrow clones would limit it to one |
| Issuer pause (real USDC) | payouts revert until unpaused; state preserved |
Stuck-escrow summary: a frozen recipient makes **that escrow wait, not disappear**; there is no path for a role to redirect funds, as required. The only escape for a permanently frozen recipient is the counter-party outcome (client refund through the arbiter, or mutual refund), which needs cooperation. Acceptance of this behaviour (O-6) and the shared-versus-factory decision (O-2) remain with the owner.

## 5. Static analysis (Slither 0.11.6, pinned Python container, `solc` 0.8.38)
12 results, 3 detectors, none High or Medium, no reentrancy finding, triaged:
| Detector | Count | Triage |
|---|---|---|
| arbitrary-send-erc20 | 1 | false positive: `fund` pulls from `t.client`, and `msg.sender == t.client` is enforced earlier in the same function |
| unused-return | 2 | intentional: the third return value of `ECDSA.tryRecover` (an error hash) is ignored; the recovered signer and the error code are both checked |
| timestamp | 9 | intentional: the deadline and review-window rules are the contract's purpose; boundaries are tested on both sides |
Measured gas (M5 table) is unchanged for existing functions; no new gas claims are made.

## 6. Limitations
1. No external audit (O-13 deferred); this is not a production-readiness claim.
2. The fork test depends on the public Fuji RPC and is off by default; it ran once (read-only). Fuji deployment and any transaction remain blocked by the human-operated-signing rule.
3. The Foundry invariant runs use a handler with a fixed party set; the gateway-level tests use the real contract on Anvil.
4. Mutual refund uses two EIP-712 signatures (no EIP-1271 contract-wallet support).
5. Transaction-level business authorization and the deployment manifest are M7/M8.
