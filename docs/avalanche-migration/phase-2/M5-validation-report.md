# P2-M5 validation report: Foundry project, simulated USDC and the escrow core

Date: 2026-11-01. Scope source: [02 contract plan](02-contract-implementation-plan.md) and the mapping in [Phase 0 03 section 7](../03-target-architecture.md). Owner decisions applied: O-1, O-3 (per-escrow arbiter snapshot only), O-4 and O-5 (preserve verified behaviour, invent no rules), O-7 (users pay gas; relayer only for release), O-2 deferred. The prompt's M5 ("smart contract implementation") is split as in the repository roadmap: M5 = core below, M6 = revision, dispute, resolution, mutual refund, invariants, security review.
Tooling: Foundry 1.8.7 (pinned Docker image), solc 0.8.38, OpenZeppelin Contracts 5.7.0, `evm_version = "cancun"`, `via_ir`. **Nothing was deployed; no Fuji request, signature or transaction; no Mainnet contact; no real funds.**

## 1. Inventory (`avalanche-contracts/`)
| File | Purpose |
|---|---|
| `src/FreelaXEscrow.sol` | shared immutable escrow: `fund`, `requestExtension`, `approveExtension`, `submit`, `release`, `pause`/`unpause`, `setDefaultArbiter`, digest views |
| `src/interfaces/IFreelaXEscrow.sol` | enums, structs, events, errors and function signatures; everything is addressed by `escrowId`, so per-escrow clones stay possible (O-2) |
| `src/mocks/MockUSDC.sol` | LOCAL_SIMULATION only: 6 decimals, open `mint`, blacklist (a blacklisted address can neither send nor receive) and issuer pause |
| `test/Base.t.sol`, `test/FreelaXEscrowCore.t.sol` | 29 tests |
| `scripts/install-deps.sh`, `scripts/forge.sh` | reproducible dependencies (OpenZeppelin 5.7.0) and the pinned Docker runner; `lib/`, `out/`, `cache/` are ignored |

## 2. Rule mapping implemented in M5 (verbatim semantics of the Anchor program)
| Rule | Implementation | Tested (both sides of every bound) |
|---|---|---|
| `fund`: client only, not paused, token equals the pinned token, amount 1..uint128, `fundingExpiresAt >= now`, `deliveryDueAt > now`, review 24..168 h, `maxRevisions <= 2`, freelancer non-zero and different from the client, arbiter equals the current default and differs from both parties, one escrow per id, +24 h only if requested and amount > 500 USDC | `fund` + `_validateTerms` | `test_fund_termBoundsAreEnforced`, `...arbiterMustBe...`, `...onlyTheClient...`, `...highValueGrace...` (exactly 500 USDC gets no grace) |
| Backend authorization (EIP-712 `FundAuthorization`) binds every term, chain id, contract, a per-escrow nonce and a deadline; the signer must hold `AUTH_SIGNER_ROLE` now; high-s signatures are refused | `_checkAuthorization`, `fundDigest` | expired at the boundary, wrong signer, revoked role, tampered terms, high-s, other contract, other chain id, and an **independent** digest computed by Foundry's `eip712HashTypedData` from JSON equals the contract's |
| `requestExtension` (freelancer; Funded; only before the original date; one extension; new date in (original, original + 7 d]) and `approveExtension` (client) | as written | bounds at +0 / +7 d / +7 d + 1, roles, once only, `now < original` boundary |
| `submit`: freelancer, non-zero evidence, Funded or Revision, if Funded then `now <= deliveryDueAt`, review clock `now + window`, authorization binds evidence hash, submission counter and nonce | as written | inclusive deadline (due and due + 1), replay, other evidence, stale nonce, wrong action type, other escrow |
| `release`: the client any time, anyone once `now >= reviewDueAt`; never twice; only Submitted; **no unilateral refund exists** | `release` | `reviewDueAt - 1` refused, `reviewDueAt` allowed for a relayer; client early; second release |
| Pause blocks only `fund`; release and extension keep working | `whenNotPaused` on `fund` only | `test_pauseBlocksOnlyFundingNotReleaseOrExtension` |
| Roles: pauser pauses, admin unpauses and sets the default arbiter for **new** escrows only, the authorization signer has no other power; nobody can reach escrowed funds or change an existing escrow's arbiter | OpenZeppelin `AccessControl`, `Pausable` | `test_roles_*`, `test_noRoleCanReachEscrowedFunds...` (direct token sends are not claimable; payout never exceeds the deposit) |
| Token failure modes | `SafeERC20`, effects before interactions, `nonReentrant` on `fund` and `release` | blacklisted freelancer: release reverts and the escrow stays Submitted with its funds; blacklisted client: no escrow is left behind; issuer pause; missing approval; a malicious re-entering token cannot re-enter `release` |

## 3. Results
`forge test`: **29 passed, 0 failed** (including a 256-run fuzz of fund, submit and release over amount, review window and wait time: payout equals the deposit and the contract ends empty). `forge build` succeeds; the compiler's only output is the `block-timestamp` lint note, which is intentional because the deadline rules are the contract's purpose.
Four tests failed first because of Foundry cheatcode ordering in the tests themselves (a `prank` or `expectRevert` was consumed by a role getter or a signing helper evaluated as an argument); the tests were corrected, not the contract.
Measured gas of the shared baseline (median of the successful path, from `forge test --gas-report`): `fund` 224,795; `submit` 91,174; `release` 85,180; `requestExtension` 29,072; `approveExtension` 28,975.

## 4. O-2 gate (shared contract versus factory/clones): stays DEFERRED, no decision invented
Implemented: the approved shared immutable baseline behind an escrowId-addressed interface. **Not measured:** a factory/EIP-1167 variant was not built, so no gas comparison exists and none is claimed. Qualitative blast-radius facts, as already recorded in the plan: with a shared contract a USDC blacklist of the contract address would freeze every open escrow, a per-escrow clone would limit that to one; the shared design has the smaller audit surface. M5 evidence relevant to the decision: the blacklist tests above show that a blacklisted recipient blocks only that escrow's release under the push-payout baseline (state preserved, retry after unblacklisting). The decision remains the owner's.

## 5. Limitations
1. Revision, dispute, resolution and mutual refund are M6; until then a Submitted escrow can only be released.
2. `MockUSDC` is a simulation token; the Circle Fuji USDC interaction (approve then fund) is tested against the real token in M6 (fork, read-only RPC).
3. The mutual-refund mechanism (two EIP-712 signatures) follows the owner's "preserve behaviour" answer for question 10 and is implemented in M6.
4. No deployment script or manifest yet (M7). No external audit (O-13 deferred); this is not a production-readiness claim.
