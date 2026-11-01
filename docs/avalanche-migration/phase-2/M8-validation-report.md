# P2-M8 validation report: gateway REST API, neutral port extension and the marketplace adapter

Date: 2026-11-02. Scope source: [03 backend plan, P2-M8](03-backend-implementation-plan.md) and [Phase 0 03 section 2](../03-target-architecture.md). The prompt's M8 ("frontend") is the repository's M10/M11; the repository roadmap is followed. Owner decisions applied: O-7 (users pay testnet gas; the relayer only releases), O-10/O-17 (the full ten-state model is exposed, nothing is hidden), O-11 (backend development wallets only in LOCAL_SIMULATION).
**No Fuji request, transaction or signer; no Mainnet contact; no real funds.** All chain activity was on a local Anvil.

## 1. Gateway API (`avalanche-integration`, `/api/v1/avalanche`)
| Endpoint | Purpose |
|---|---|
| `GET /config` | environment, chain id, escrow, token, token kind, contract version, paused flag, default arbiter, whether backend signers exist, explorer base URL (Fuji only) |
| `GET /addresses/validate`, `GET /assets/balance` | address check; token balance in base units (the simulation balance) |
| `POST /assets/faucet` | mints the **simulated** USDC; only for the mock token in LOCAL_SIMULATION, capped at 1,000,000 USDC per call |
| `GET /escrows/{id}` | on-chain escrow record (contract state wins) |
| `POST /escrows/{id}/actions/{action}/prepare` | calldata for a user's own wallet (fund returns approve then fund) plus the Java-built backend authorization; mutual refund returns the typed data and digest for both parties to sign |
| `POST /escrows/{id}/actions/{action}/execute` | LOCAL development wallets only: the signer role is chosen from the action, never by the caller; idempotent by operation key |
| `GET /transactions/{operationKey}`, `GET /transactions/by-hash/{hash}` | tracked transaction state; live read-only status of a transaction a wallet broadcast itself (an unknown hash is UNKNOWN, never failed) |
Actions: `fund`, `extension-request`, `extension-approve`, `submit`, `revise`, `dispute`, `release`, `resolve`, `mutual-refund`.
Every response carries `environment` and `isSimulated`. Security: every POST needs `X-Internal-Api-Key` (blank key refuses all POSTs, constant-time compare, mirroring the Solana gateway); JSON binding rejects unknown properties, so a request cannot carry a key, an RPC URL or a chain id (tested with `privateKey`, `rpcUrl`, `chainId`); errors are stable codes without internals. **In a process with no backend signers (the AVALANCHE_FUJI shape) `prepare` for fund, submit and revise reports `AUTHORIZATION_UNAVAILABLE`, `execute` and the faucet report `SIGNER_UNAVAILABLE`; read-only calls and prepared actions that need no authorization (for example release) still work for a wallet to sign.**

## 2. Marketplace (`marketplace-backend`, Java 17, REST only)
Additive neutral types: `TransactionReference`, `TransactionStatus` (all ten states; only FINALIZED is a successful settlement), `TransactionResult`, `PreparedTransaction`/`PreparedAction`, `EscrowActionRequest`, `EscrowSnapshot` with `NeutralEscrowStatus`, `EnvironmentInfo`. `BlockchainGateway` gained default methods that throw `UnsupportedOperationException`, so the Solana adapter needed **one wiring annotation (`@Primary`)** and no logic change. New: `AvalancheClient` (same RestTemplate conventions as `SolanaCprClient`), `AvalancheGatewayAdapter`, `AvalancheEscrowIds` (milestone UUID to `bytes32`, reversible), `BlockchainGatewayRegistry` (selects a gateway by network id, refuses two gateways for one network). Everything Avalanche-related in the marketplace is **off by default** (`avalanche-gateway.enabled=false`): with the flag off no Avalanche bean exists and behaviour is identical to before. The port contains no web3j, ABI or Solana types.

## 3. Results
| Check | Result |
|---|---|
| Gateway suite (JDK 21): all earlier tests plus `AvalancheApiTest` (6) | 104 run, 101 passed, 0 failed, 3 optional skipped; identical on three consecutive runs |
| `AvalancheApiTest` over real HTTP, real contract, Anvil and MySQL | config; internal-key enforcement and unknown-property rejection; input validation; the **full lifecycle through the API only** (faucet, fund, submit, revise, resubmit, client release; dispute and arbiter refund; mutual refund signed by both dev wallets; status by hash); idempotent operation key; a process without backend signers |
| Marketplace new tests (JDK 21 build, Java 17 target) | adapter 12, client 4 (against a local HTTP stub using the gateway's field names): all pass |
| Marketplace **full suite** | **364 run, 0 failed, 0 errors** |
| Solana gateway module | 10/10; JDK 17 compile of marketplace and Solana modules passes |
| Contract shape | gateway tests assert the JSON fields the marketplace records read, so drift fails a test on the gateway side |
A defect found by repetition and fixed: re-sending an identical `execute` request re-prepared the action, and the fresh authorization deadline changed the signed calldata, so `SubmissionService` reported a conflict. The API now returns the existing transaction for a repeated operation key (a different target is still a conflict); a deterministic test forces a later block timestamp between the two calls. Another interaction fixed: the API test's Spring context (with its reconcile timer) outlived its class and reconciled other tests' rows in the shared database; it is now closed after the class.

## 4. Limitations
1. Fuji: `prepare` for authorized actions and `execute` cannot work there by the M2 owner rule; a human-operated signing procedure is still required (BLOCKED).
2. The gateway trusts the marketplace to send only terms taken from recorded business state (the internal key is the only caller check); M9 enforces that on the marketplace side before any call.
3. User-wallet transactions are tracked only by a live by-hash read; they are not stored by the gateway (the marketplace persists the hash and polls).
4. The on-ramp (`onrampReceiptExists`) has no Avalanche equivalent: simulated USDC comes from the faucet; the adapter says so explicitly.
