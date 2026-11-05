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
| 7 | LOW | When a party has no usable EVM wallet, the client's funding attempt failed with the generic "Dữ liệu không hợp lệ" | **Fixed** (2026-11-05, fourth session): `AVALANCHE_WALLET_NOT_LINKED` (4070, HTTP 409) says that both Client and Freelancer must link a valid EVM wallet, without saying who; unit test |
| 8 | LOW | `execute` without an idempotency key derived the operation key from a count; two concurrent calls could collide on the unique key | **Fixed**: without a key every call is its own operation (random key); with a key the behaviour is unchanged (same key, same row). Unit test; no persisted value changed |
| 9 | LOW | The gateway protects mutating calls with a constant-time internal key but its read endpoints (transactions, escrow views, balances) need no key | **Analysed, deliberately not changed** (see "Finding 9 analysis"); compensating control now checked by `validate_env.mjs` |
| 10 | INFORMATIONAL | The administrator decision moves the milestone to `RELEASE_PENDING` / `REFUND_PENDING` before the arbiter transaction is mined (as on the Solana escrow); if the arbiter never executes it, the chain stays Disputed and the parties can still settle by mutual settlement (R12, covered by the A2 flow) | By design; recorded |
| 11 | INFORMATIONAL | A pre-funding cancellation of an Avalanche contract is accepted and can race an already prepared `fund` transaction; the escrow's own funding expiry bounds it | Recorded; not changed |
| 12 | INFORMATIONAL | Legacy cancellation and settlement services need a fiat funding record, so they fail closed for a funded Avalanche contract (by code reading) | Recorded |
| 13 | INFORMATIONAL | Backend development signers exist only in LOCAL_SIMULATION; the application refuses them elsewhere (EnvironmentGuardTest, StartupGuardContextTest) | Verified by tests |
| 14 | MEDIUM (robustness) | An empty `eth_call` answer (an address without contract code, for example after the chain was reset) made the escrow reader parse an empty hex string; every gateway request then failed with an unexplained HTTP 500 for as long as the condition lasted | **Fixed**: `UnreadableChainStateException` mapped to HTTP 502 `CHAIN_STATE_UNREADABLE`; unit tests. Found by the Anvil restart scenario (the first compose run lost the chain because of finding 15) |
| 15 | LOW (operations) | The compose Anvil container ran as an unprivileged user and could not write its state file to a fresh named volume, so a restart silently reset the chain | **Fixed** in the Anvil override file (`user: "0:0"`, local only); the restart scenario now passes |
| 16 | LOW | The escrow panel let any wallet account proceed to the transaction step; the escrow would reject an account that is not the linked wallet, after sending an approve | **Fixed**: `ACCOUNT_NOT_LINKED` before anything is signed or sent; unit test and browser step S7a |
| 17 | INFORMATIONAL | The UI does not subscribe to wallet events (accountsChanged, chainChanged, disconnect). It queries the wallet again at every action and refuses a changed account or network at that moment, so it never acts on a stale account | Verified by the browser run (S3, S7a, S7b, S9) |

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

## Finding 7 to 9: analysis
- **7 (generic error).** Impact: usability only. The error code is new and additive; the message does not tell the client whose wallet is missing. Fixed.
- **8 (count-based key).** Before: two simultaneous calls without a key could pick the same count; the second then failed on the unique key (fail closed, no double
  submission). After: each such call is a separate operation, which is what "no key" means; a caller that wants one operation sends a key. Fixed.
- **9 (gateway reads).** What a keyless GET can read: chain configuration and manifest addresses, escrow state by id, balances, and the gateway's own
  transaction records (action, hash, status, error class). The same facts are public on a chain explorer, except the mapping between a marketplace milestone and an
  escrow id. What it cannot do: submit, sign, mint or change anything (every mutating call needs the key; the faucet is LOCAL only). Requiring the key on reads
  would also require the marketplace client to send it on reads, which an existing unit test fixes the other way on purpose ("reads carry no internal key"), and it would
  change how operators and the harness inspect the gateway. That is an authentication policy change, so it was **not made**. Recommendation if the gateway is ever
  reachable from outside its host: add an opt-in setting (default off) that requires the key on reads and make the client send it; until then the compensating
  control is that compose publishes every port on the loopback interface only, which `validate_env.mjs` now checks.

## Outage and recovery (compose-built images, LOCAL_SIMULATION)
Scenario results are in [local-runbook.md](phase-2/local-runbook.md) and the evidence folder `phase-2/evidence/compose-avalanche-20261105`. They are safety and convergence
checks, not availability guarantees: during a gateway, RPC or database outage the marketplace answers with HTTP 500 or a timeout (observed, not designed), and nothing is applied early.

