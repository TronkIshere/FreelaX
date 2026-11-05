# Avalanche migration: production-readiness and security review (2026-11-05)

Scope: the LOCAL_SIMULATION implementation (contracts, gateway, marketplace rail, frontend). Method: code reading, the existing Slither
triage (M6, 12 results, no change since), the Foundry suites (77 passed, 1 skipped), the Java and frontend suites, the A2 harness and the
browser validation. **This is not an audit and does not claim the project is production-secure.** There is no external audit and no key
ceremony (O-13, DEFERRED by the owner); nothing here was exercised on Fuji or Mainnet.

## Findings
Severity words: CRITICAL, HIGH, MEDIUM, LOW, INFORMATIONAL. "Fixed" means a code change plus a test that fails without it (mutation check
on 2026-11-05 for the four guards of the second session).

| # | Severity | Finding | Status |
|---|---|---|---|
| 1 | HIGH | A contract carrying an Avalanche network id could be funded through a legacy path (fiat, Solana escrow, partner escrow), so the milestone reached FUNDED with no chain evidence, and the dispute and submission checks trusted that state | **Fixed** (entry and completion guards on the three paths, applied-`fund` proof for disputes; tests; commits `d138e92`, `f27e1cb`) |
| 2 | HIGH | The partner funding completion called the partner payment backend before the Avalanche guard ran | **Fixed** (check first, record marked UNKNOWN; tests) |
| 3 | MEDIUM | A party that also held the administrator role could authorize the arbiter `resolve` of its own contract | **Fixed** (`resolve` refuses a party; test) |
| 4 | MEDIUM | An administrator decision could not be recorded for an Avalanche dispute, and `dispute` was marked applied without reading the chain, and an arbiter `resolve` did not close the dispute | **Fixed** (anchored-dispute requirement, chain read, dispute closed from the chain outcome; tests; A2 live flow) |
| 5 | MEDIUM (functional, not a security defect) | The marketplace view reported the rail as unavailable for a contract that had not chosen it, so the browser UI could never offer the choice | **Fixed** (offered only while `enable()` would accept it; test; found by the browser validation) |
| 6 | LOW | The wallet adapter's English error texts were shown to Vietnamese users | **Fixed** (translated by error code; test) |
| 7 | LOW | When the freelancer has no linked EVM wallet, the client's funding attempt fails with the generic "Dữ liệu không hợp lệ" (the detail is hidden on purpose) | OPEN (usability; no security effect) |
| 8 | LOW | `execute` without an idempotency key derives the operation key from a count; two concurrent calls can collide on the unique key and one fails instead of returning the first row | OPEN (fails closed: no double submission) |
| 9 | LOW | The gateway protects mutating calls with a constant-time internal key but its read endpoints (transactions, escrow views, balances) need no key | OPEN (compose publishes the port on 127.0.0.1 only; do not expose it) |
| 10 | INFORMATIONAL | The administrator decision moves the milestone to `RELEASE_PENDING` / `REFUND_PENDING` before the arbiter transaction is mined (as on the Solana escrow); if the arbiter never executes it, the chain stays Disputed and the parties can still settle by mutual settlement (R12, covered by the A2 flow) | By design; recorded |
| 11 | INFORMATIONAL | A pre-funding cancellation of an Avalanche contract is accepted and can race an already prepared `fund` transaction; the escrow's own funding expiry bounds it | Recorded; not changed |
| 12 | INFORMATIONAL | Legacy cancellation and settlement services need a fiat funding record, so they fail closed for a funded Avalanche contract (by code reading) | Recorded |
| 13 | INFORMATIONAL | Backend development signers exist only in LOCAL_SIMULATION; the application refuses them elsewhere (EnvironmentGuardTest, StartupGuardContextTest) | Verified by tests |

## Areas checked and what was seen
- **Smart contract access control:** funding by the client only, submit by the freelancer only, `resolveDispute` by the snapshotted arbiter only,
  parties and arbiter must differ, default arbiter changed only by the admin role, pause by the pauser role. No privileged withdrawal exists
  (O-6). `nonReentrant` on every function that moves tokens; `SafeERC20`; EIP-712 digests bind chain, contract, nonce and deadline; ECDSA
  rejects malleable signatures; `mutualSettleDispute` needs both parties' signatures and only from Disputed (22 Foundry tests, incl. a swapped
  signature).
- **Replay and idempotency:** per-escrow `authNonce` increments on every authorized action; held marketplace signatures are deleted when the
  action is applied; the administrator decision is idempotent for the same key and conflicts for another.
- **Chain and database consistency:** business state moves only after FINALIZED and an agreeing chain read (`CHAIN_MISMATCH` otherwise), except
  the administrator decision noted in finding 10. A2 and the browser run show both layers agreeing after success and after failure.
- **Wallet boundary:** the backend never signs for a user on Fuji; the browser run shows that a rejected signature or transaction, an
  unsupported or wrong network and an invented transaction hash leave the backend state unchanged.
- **Secrets:** `.env` is ignored and not tracked; `.env.example` holds placeholders only (`validate_env.mjs`); the evidence checkers refuse key
  material; no log line of the reviewed gateway and marketplace code prints a key or password.
- **RPC failure handling and retries:** covered by the M3 and M4 tests and the combined fault scenario (timeouts, 429, duplicate, revert,
  restart); not re-verified on a public RPC.

## Not assessed
Formal verification; fuzzing beyond the Foundry invariant suite; chain reorganization behaviour beyond the tests; load and denial-of-service
behaviour; dependency vulnerability scanning; the legacy Solana modules; Fuji behaviour of the escrow (it is not deployed there).
