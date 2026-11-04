# Phase 3 P3-C3: simulation drills (LOCAL_SIMULATION, 2026-11-04)

Status: executed under delegated engineering authority (R6 still **not owner approved**). Every drill below maps to tests
that ran on 2026-11-04: Foundry 1.8.7 **55 passed, 1 skipped** ([evidence](../phase-2/evidence/forge-test-2026-11-04.txt)),
gateway JDK 21 115 run / 0 failures / 3 skipped, and the A2 single-stack run
([evidence](../phase-2/evidence/LOCAL_SIMULATION-a2-local-stack-with-p3c4-20261104.json)). No contract was changed.

| Drill | Expected behaviour | Evidence (2026-11-04) | Result |
|---|---|---|---|
| **Pause** | contract pause blocks only new funding; release, extension, dispute and resolution keep working | `test_pauseBlocksOnlyFundingNotReleaseOrExtension`, `test_resolve_pauseDoesNotBlockDisputeOrResolution`, `test_fund_onlyTheClientDuplicatesAndPauseAreRefused` | PASS |
| **Signer rotation** | a revoked authorization signer is refused; each role is separate; rotating the default arbiter never touches an existing escrow | `test_authorization_wrongSignerRevokedRoleAndTamperedTermsAreRefused`, `test_roles_eachCapabilityIsSeparate`, `test_roles_changingTheDefaultArbiterNeverTouchesAnExistingEscrow`, `test_resolve_arbiterSnapshotSurvivesDefaultArbiterRotation`; live: A2 hands `AUTH_SIGNER_ROLE` and the default arbiter to a fresh signer set on every run (F3) | PASS |
| **Backend offline: release** | release needs no backend authorization: the client at any time, anyone after the review window | `test_release_clientAnytimeAnyoneAfterTheReviewWindow`, `test_release_clientMayReleaseBeforeTheDeadlineAndNeverTwice`; the release calldata is `release(escrowId)` with no signature parameter | PASS |
| **No role reaches funds** | no admin, arbiter, relayer or authorization role can withdraw escrowed funds | `test_noRoleCanReachEscrowedFundsAndDirectTransfersAreNotClaimable` | PASS |
| **Token issuer controls (O-6)** | a blacklist or issuer pause leaves the escrow in place, reported, never bypassed; lifting it restores the normal path | Foundry `test_usdc_blacklistedFreelancerBlocksReleaseWithoutLosingTheEscrow`, `test_usdc_blacklistedClientCannotFundAndIssuerPauseBlocksBoth`; gateway `AvalancheApiTest` 4d (blacklist + pause reported, lifted, then submit and release); A2 live (client blacklisted → `CLIENT_BLACKLISTED` in the marketplace view → lifted → mutual refund succeeds) | PASS |
| **Arbiter loss** | what happens to a disputed escrow if the snapshotted arbiter key is lost | `resolveDispute` requires the snapshotted arbiter (O-3: no reassignment); `mutualRefund` accepts only Funded, Submitted or Revision, **not Disputed** (`FreelaXEscrow.sol` line 249) | **FINDING F14**: a Disputed escrow whose arbiter key is lost cannot be settled on chain. Before a dispute, parties can still agree a mutual refund. Any fix changes the contract or O-3: **owner decision R12** |

Not drilled: a real Fuji provider outage, real issuer actions on Circle's Fuji USDC (needs C1/B1), and an external audit (O-13
DEFERRED).
