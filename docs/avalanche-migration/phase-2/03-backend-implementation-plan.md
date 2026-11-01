# 03. Backend implementation plan (plan only)

Each milestone is small enough to review alone. Class and package names are **placeholders**. Common rules: Java 21 and web3j 6.1.0 only inside `avalanche-integration`; marketplace stays Java 17; Solana code and persisted enums untouched; additive Flyway only; environment guards from [05](05-environment-and-signer-safety-plan.md) active from M1; state machine from [06](06-transaction-lifecycle-and-reconciliation-plan.md).

## P2-M1 Module skeleton, configuration, environment guards, web3j validation
- **Objective:** a buildable, startable `avalanche-integration` Spring Boot service that refuses to start unless the environment is valid, and proves web3j 6.1.0 works in the real application context.
- **Affected modules:** new `avalanche-integration`; root `pom.xml` (one `<module>` line, nothing else); `docker-compose.yml`/`.env.example` additions only if the owner approves (otherwise documented, not edited).
- **Expected classes:** `AvalancheApplication`, `EnvironmentProperties`, `EnvironmentGuard` (`ApplicationRunner`/startup validator), `EndpointClass`, `ChainAllowList`, `ChainClient` (web3j `Web3j` bean with explicit OkHttp timeouts), `RpcPacer`, `HealthController`.
- **Dependencies:** none beyond Spring Boot 3.5.16 starters and `org.web3j:core:6.1.0`; the Jackson arrangement is **tested, not assumed**: candidate A explicit `jackson-annotations:2.22` (compatible with the root's `jackson-bom.version` 2.21.7 pin, Phase 1), candidate B aligned `jackson-bom` 2.22.x for this module only. Pick the one that passes the acceptance test; record it.
- **Acceptance:** module builds alone (`-pl avalanche-integration`); context starts with a valid LOCAL_SIMULATION config; **a real JSON-RPC call from a Spring-managed bean through the embedded web server succeeds** (the exact failure Phase 1 reproduced, `NoClassDefFoundError JsonApplyView`, must not occur); startup **fails** on: missing environment, Mainnet chain id 43114 or 1, unlisted chain id, Mainnet endpoint host (rejected **before any request**), loopback endpoint under AVALANCHE_FUJI, non-loopback under LOCAL_SIMULATION, client-version mismatch (`anvil/` expected locally, `avalanchego/` on Fuji), a secondary provider with a different chain id.
- **Security:** no default environment, no property that adds chain ids, RPC URLs redacted in logs, no secrets in config.
- **Tests:** unit tests of the guard table (the Phase 1 `EnvGuardTest` cases are the seed); Spring context tests for each failing configuration; web-context smoke test with a real call on Anvil (and a Fuji read-only profile that is skipped offline).
- **Rollback:** remove the module entry and folder; nothing else references it.

## P2-M2 Development signers
- **Objective:** ephemeral signers, one per role and environment, behind a `Signer` interface.
- **Affected modules:** `avalanche-integration` (`signer`, `auth` packages).
- **Expected classes:** `Signer { address(); sign(byte[] digest32) -> Signature(r,s,v) }`, `SignerRole` (AUTHORIZATION, ARBITER, RELAYER, PAUSER, ADMIN, DEPLOYER, plus test buyer/freelancer for LOCAL only), `DevSignerFactory` (in-memory generation; optional explicit local-file load), `SignatureNormalizer` (low-s, recovery id), `Eip712Digest`.
- **Dependencies:** M1.
- **Acceptance:** each role gets a distinct key; a signer built for one environment is rejected in the other; no key is printed, logged or persisted unless an explicit `LOCAL_FILE` source is configured (path outside the repository, announced at startup, address only in logs); signatures are low-s and recover to the signer address; EIP-712 digests equal the contract's.
- **Security:** KMS **deferred** (owner decision); balance cap refuses a Fuji signer holding more than a testnet-scale amount; Mainnet signing impossible (no signer can be built for a chain id outside the allow-list); limitations documented in [phase-1 security evaluation](../phase-1/security-key-management-evaluation.md).
- **Tests:** per-role isolation, cross-environment refusal, low-s and recovery over many random digests (Phase 1 K1 as seed), digest equality against a Foundry-produced vector, log scan proving no key material is emitted.
- **Rollback:** delete the package; no persisted keys exist by default.

## P2-M3 Chain client, nonce allocator, transaction tracker
- **Objective:** submit EVM transactions safely and track them with the validated state machine.
- **Affected modules:** `avalanche-integration` (`tx`, `chain`, `db`).
- **Expected classes:** `SubmissionService` (`submit(operationKey, role, to, data, gas)`), `NonceAllocator` (row lock per signer), `TxTracker`/`Reconciler` (the Phase 1 prototype logic, hardened), `ErrorClassifier` (definitive vs non-definitive), `RpcPacer`, `TxRepository`; Flyway `V1__avalanche_gateway.sql` (tables in [01](01-target-module-architecture.md)).
- **Dependencies:** M1, M2; calibration of fee bumps on Fuji needs the funded wallet (default to a conservative bump and mark as "to calibrate").
- **Acceptance:** all behaviours of Phase 1 scenarios S1-S8 reproduced against Anvil with the real database: operation-key idempotency (one transaction per business operation), raw tx and hash persisted **before** send, timeout -> UNKNOWN with no second send, receipt status 0 -> REVERTED, replaced nonce -> REPLACED only after receipt null + by-hash null + nonce consumed, null/HTTP 429 never become failure, restart recovery, environment isolation of rows; concurrent submissions from many threads never reuse a nonce.
- **Security:** a transaction is signed only for the validated chain id; gas limit caps and fee caps; no signing for an address outside the role's signer.
- **Tests:** unit (classifier, state transitions), Testcontainers or compose database for the allocator, Anvil integration, fault proxy (hold response, null lookups, HTTP 429 with `Retry-After`), concurrency test.
- **Rollback:** additive tables dropped with the schema; module disabled by removing it from compose.

## P2-M4 Log scanner, cursor, reconciliation, provider profiles
- **Objective:** recover missed or duplicated events and cross-check chain state, restart-safe.
- **Affected modules:** `avalanche-integration` (`scan`); the existing `marketplace-backend` schedulers consume results through the port in M9 (no scheduler is added to the marketplace here).
- **Expected classes:** `LogScanner`, `ScanCursorStore`, `EventDecoder`, `ProviderProfile` (`maxLogRange` default **100**, `requireAddress`, pacing), `WsHintListener` (latency hint only), `Reconciler` (event vs `eth_call` state).
- **Dependencies:** M3, deployed or mocked contract ABI (M5/M6 for real events; the scanner is first tested on the USDC `Transfer` event as in Phase 1).
- **Acceptance:** cursor advances only with the recorded events in one transaction; scan stops at the finalized block (Fuji) or confirmation depth (Anvil); **crash and restart equals a single pass** (Phase 1: 41 of 41 against an independent provider); replayed chunk suppressed by `(txHash, logIndex)`; WebSocket gap recovered by bounded reads; chunk size obeys the provider profile and halves on a range error; HTTP status is checked **before** JSON parsing; `Retry-After` honoured; pacing about 1 request/s per host for background work.
- **Security:** only logs from the configured contract address and known topics are accepted; rows of other environments ignored.
- **Tests:** replay of Phase 1 cursor test on Anvil, duplicate subscription test, fault proxy (429/HTML, null), provider profile tests (100, 50,000 with address required, 100 free-tier shape).
- **Rollback:** disable the scheduler flag; cursor tables additive.

## P2-M8 Gateway REST API, neutral port, adapter
- **Objective:** expose the gateway to the marketplace through the existing architecture style, extending the port only when a consumer moves.
- **Affected modules:** `avalanche-integration` (`api`), `marketplace-backend/.../blockchain/` (port types) and `.../blockchain/avalanche/AvalancheGatewayAdapter`, `client/AvalancheClient`.
- **Expected types:** neutral `TransactionReference`, `TransactionStatus` (CREATED..UNKNOWN + REPLACED), `PreparedTransaction` (EVM: `{to,data,value,chainId}`, authorization, environment label), port methods in the order of 03 section 2 (`validateAddress`, `assetBalance`, `prepareEscrowFund`, `escrowState`, `submitEscrowAction`, `transactionStatus`).
- **Acceptance:** adapter tests mirror `SolanaGatewayAdapterTest`; **all existing Solana tests pass unchanged**; responses carry `environment` and `isSimulated`; the port contains no web3j/ABI/PDA types.
- **Security:** internal API protection like the existing gateway; no endpoint accepts a private key, an RPC URL or a chain id from the caller.
- **Tests:** contract tests against a gateway stub, adapter unit tests, Solana regression suite.
- **Rollback:** remove the adapter bean/flag; the port additions are additive.

## P2-M9 Marketplace integration (disabled or secondary configuration)
- **Objective:** connect the existing business flows to Avalanche without changing Solana behaviour.
- **Affected modules:** `marketplace-backend` (consumers moved behind the port in the ADR order: `Unified*`, `PaymentFlowService`, `SolanaEscrow*`, `Contract*`), additive Flyway migration (new columns: chain/environment, tx hash, nonce, block; **no renames**), MISA chain literal and explorer URL parametrised per record.
- **Acceptance:** with Avalanche disabled, behaviour and tests are identical to today; with it enabled on Anvil, a contract can be funded, submitted, released, refunded and disputed end to end; the backend only produces authorizations whose terms equal the MySQL records; release works with the backend stopped (permissionless).
- **Security:** both adapters cannot sign for the wrong chain; every record carries environment and `isSimulated`; no persisted enum changes.
- **Tests:** full existing suites, new adapter-backed service tests, migration test on a **copy** of a database, rail regression matrix.
- **Rollback:** flag off; migration additive and ignorable.
Rail literal (new `AVALANCHE_ESCROW` vs `UNIFIED_USDC_PAYOUT` plus a chain column) is an open decision (O-9).

## M0 (no code) and ordering reminder
M0 collects owner approval and the decisions that gate M1/M5/M6 ([09](09-unresolved-decisions-and-prerequisites.md)). M10-M12 are in [04](04-frontend-integration-plan.md) and [07](07-test-strategy.md).
