# P2-M1 validation report: Avalanche integration service skeleton

Date: 2026-11-01. Scope: [SIMULATION_SCOPE](../../SIMULATION_SCOPE.md). Environments touched: LOCAL_SIMULATION (a stub and a local Anvil) and AVALANCHE_FUJI (read-only `eth_chainId` and `web3_clientVersion` only). No transaction, signature, deployment or Mainnet request.
Commands run with `JAVA_HOME` set per process to JDK 21 (`C:\Program Files\Java\jdk-21`); Maven 3.9.16 from the local wrapper distribution (`mvn` is not on `PATH`).

## 1. Solana baseline (before any change) and after
| Step | Command (repo root) | Before | After |
|---|---|---|---|
| Reactor compile | `mvn -B -ntp -o test-compile` | BUILD SUCCESS (4 modules) | BUILD SUCCESS (5 modules incl. avalanche-integration) |
| Solana tests | `mvn -B -ntp -o -pl solana-integration,marketplace-backend test -Dtest='SolanaGatewayAdapterTest,SolanaCprClientSafeErrorTest,InvoicePaymentsInstructionsTest,LegacyTransactionCodecTest,LocalSignerRegistryTest,SolanaAddressesTest,SolanaValueCodecTest,SolanaIntegrationApplicationTest' -Dsurefire.failIfNoSpecifiedTests=false` | marketplace 6/6, gateway 10/10 | marketplace 6/6, gateway 10/10 |
| JDK 17 | `mvn -o -pl solana-integration,marketplace-backend test-compile` (corretto 17.0.10) | n/a | BUILD SUCCESS |
This is a minimal baseline, not the full legacy suite.

## 2. Jackson/web3j revalidation in the real service
| Run | Arrangement | Result |
|---|---|---|
| 1 | no override (web3j 6.1.0 on the root's Jackson 2.21.7 pin) | **FAILED**: `NoClassDefFoundError: com/fasterxml/jackson/annotation/JsonApplyView` in the real Spring context. The Phase 1 failure reproduces in the actual service. |
| 2-3 | A: explicit `jackson-annotations:2.22` in this module only | context starts, web3j call through the web server succeeds |
| 4 | B: `-Djackson-bom.version=2.22.4` for this module | same success |
**Chosen: A** (smallest change; the Jackson 2 BOM and every other module stay untouched). Resolved tree: `jackson-databind 2.21.7`, `jackson-annotations 2.22`, `tools.jackson` (Jackson 3) `3.2.3`, `okhttp 5.5.0`, `web3j core 6.1.0`.
A real finding from run 2: on HTTP 429 web3j throws an **unchecked** `ClientConnectionException` (body embedded). `ChainClient` now wraps `IOException | RuntimeException` so throttling is classified as a non-definitive failure instead of leaking.

## 3. Acceptance criteria
| Criterion | Evidence |
|---|---|
| Starts in LOCAL_SIMULATION | stub (`WebContextStubTest`), real Anvil 1.8.7 (`AnvilWebContextTest`, packaged jar, JDK 21 container) |
| Starts in AVALANCHE_FUJI only with valid configuration | `FujiReadOnlyWebContextTest` (opt-in, `AVALANCHE_TEST_FUJI=true`): `api.avax-test.network`, chain 43113, `avalanchego/` |
| Invalid configuration fails startup | `StartupGuardContextTest` (10 Spring-context cases) + `EnvironmentGuardTest` (11); packaged jar and container exit 1 without `AVALANCHE_ENVIRONMENT` |
| Mainnet endpoint rejected before network access | guard runs inside the `chainClient` bean factory before `Web3j` is built; test asserts the failure comes from that bean; unit test proves the check is pure; jar with `api.avax.network` exits 1 |
| Real web3j call through the web context | status request performs 2 live JSON-RPC calls (asserted: exactly 2 requests, only `eth_chainId` and `web3_clientVersion`) |
| No Solana functionality broken | section 1 |
Test totals (after the owner-review additions in section 6): default build 24 run, 22 pass, 2 skipped (the optional Anvil and Fuji tests, off by default so the default build never depends on a public RPC); Anvil test 1/1; Fuji read-only test 1/1.

## 4. Guard behaviour implemented
Environment required (no default; unknown values such as `MAINNET` fail binding). Endpoint pre-check without I/O: http(s) only, no credentials, Mainnet hosts (`*.avax.network`, any URL containing `mainnet` or `43114`) rejected in every environment; LOCAL_SIMULATION accepts only local-class hosts (loopback, `localhost`, `host.docker.internal`, single-label compose names); AVALANCHE_FUJI requires https and a compiled host allow-list (`api.avax-test.network`, `avalanche-fuji-c-chain-rpc.publicnode.com`). Chain id allow-list compiled in (LOCAL: 31337, 43113; FUJI: 43113; 43114, 1, 0 rejected with a dedicated code). Client family `anvil/` (local) or `avalanchego/` (Fuji). Optional secondary provider must be same environment, chain id and family. Only the host is ever logged.
Deliberate choices, all **accepted by the owner** (section 6): the Fuji host allow-list, single-label hostnames as local-only, and the JDK-activated reactor profile.

## 5. Not in M1
No database, nonce, signer, submission, scanner, contract or REST mutation endpoint exist. `init-db.sql` (`avalanche_gateway`) is deferred to M3, where the database is first used. The internal API key filter arrives with the first mutating endpoint (M8).

## 6. Owner review and final decisions (2026-11-01)
| # | Decision | Verified against the code |
|---|---|---|
| 1 | **Accepted:** JDK-activated Maven profile; JDK 21 validation is mandatory for the Avalanche module; JDK 17 builds **intentionally exclude** Avalanche | root `pom.xml` profile `avalanche-integration` activated by `<jdk>[21,)</jdk>`; reactor shown as 5 entries on JDK 17 and 6 on JDK 21 |
| 2 | **Accepted:** Fuji RPC host allow-list is exactly `api.avax-test.network` and `avalanche-fuji-c-chain-rpc.publicnode.com`; any further provider needs explicit review | `EnvironmentGuard.FUJI_HOSTS`, exact `Set.contains` on the lower-cased host; new test `fujiAllowListIsAnExactHostMatch` proves look-alike hosts (`api.avax-test.network.evil.example`, `evil.api.avax-test.network`, `avax-test.network`) are rejected and the match is case-insensitive |
| 3 | **Accepted:** single-label hostnames such as `anvil` only in LOCAL_SIMULATION; never a bypass of Fuji validation | `isLocalClass` is consulted for both environments, and under AVALANCHE_FUJI a local-class host is rejected with `FUJI_FORBIDS_LOCAL_ENDPOINT` before the allow-list check; new test `singleLabelHostnamesAreLocalOnlyAndNeverPassFujiValidation` (`anvil` over http and https, `host.docker.internal`) |
| 4 | **Accepted deferral:** `CREATE DATABASE avalanche_gateway` in `init-db.sql` is **deferred to M3**, not done | `init-db.sql` unmodified (`git diff` shows no change); M1 has no database |
Review of the implementation found no defect. The review added the two guard tests above (M1 test scope only; no production code changed). Re-run on JDK 21: `mvn -B -ntp -pl avalanche-integration test` gives 24 run, 22 passed, 0 failed, 2 skipped (optional Anvil and Fuji tests).

## 7. Deferred items, limitations and required follow-ups
**Deferred O-16 item (NOT completed):** `CREATE DATABASE IF NOT EXISTS avalanche_gateway` in `init-db.sql`. To be done in M3 together with the first database use, subject to O-18 (Testcontainers for tests; separately provisioned local MySQL named `avalanche_gateway` at runtime; no `.env` reads, no unidentified database).
**JDK 21 CI validation: none exists, required follow-up.** The repository has no CI configuration at all (no `.github/workflows`, no other pipeline file found), so there is no automated JDK 21 check. Without one, a JDK 17-only pipeline silently skips the Avalanche module (by design, decision 1). Follow-up: when a pipeline exists or is added, include one step that runs on JDK 21 `mvn -B -pl avalanche-integration -am test` plus the Solana baseline commands from section 1. No CI was created in M1 and no unrelated CI overhaul is proposed.
**Limitations:**
- Building or testing the Avalanche module needs JDK 21 on `JAVA_HOME`; the development machine defaults to JDK 20, so a per-process override is required, and `mvn` is not on `PATH`.
- The Fuji allow-list means a new provider is a reviewed code change, not configuration.
- The optional Anvil and Fuji tests are off by default and run only when `AVALANCHE_TEST_ANVIL_URL` or `AVALANCHE_TEST_FUJI=true` is set; the default build never needs a public RPC.
- M1 sends only `eth_chainId` and `web3_clientVersion`; Fuji rate-limit behaviour beyond that remains as recorded in Phase 1.
- Phase 1 stays PARTIAL; its evidence and the Solana baseline in section 1 are unchanged.
