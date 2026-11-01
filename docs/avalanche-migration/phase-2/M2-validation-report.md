# P2-M2 validation report: development signers

Date: 2026-11-01. Scope: [SIMULATION_SCOPE](../../SIMULATION_SCOPE.md). Owner approval to start M2: 2026-11-01. Commands run with `JAVA_HOME` set per process to JDK 21; Maven 3.9.16 from the local wrapper distribution.
No transaction, deployment, Fuji request (M2 tests make none), Mainnet contact or real funds. No KMS. No commit.

## 1. Owner constraints and how they are met
| Owner requirement | Implementation | Evidence |
|---|---|---|
| Dev signers ONLY in LOCAL_SIMULATION; none in AVALANCHE_FUJI | `EnvironmentGuard.requireSignerEnvironment` is called by the config bean, the factory, the `DevSigner` constructor and **every `sign`** | `DevSignerFactoryTest.creationIsRefusedInAvalancheFujiForEveryRole`, `DevSignerTest.constructorRefusesNonLocalEnvironments`, `signingIsRefusedOnceTheBoundEnvironmentIsNotLocalSimulation` |
| No Mainnet signing | chain id re-checked at creation and at every sign; 43114, 1, 0 rejected | `mainnetAndUnlistedChainIdsCannotGetASigner`, `signingIsRefusedOnceTheBoundChainIdIsMainnet` |
| No KMS, no production key infrastructure | none added | code review |
| Ephemeral keys, no silent persistence | `Keys.createEcKeyPair()` per signer, held only in a private field; no file, database, property or API reads or writes keys; the `LOCAL_FILE` option from the plan was **not** built (not approved) | `keysAreEphemeralAndDifferPerInstance`; a packaged-jar run in an empty working directory left it empty (1 entry before and after) |
| Never log, commit, expose or document private keys | only addresses are logged; `Signer` has no key accessor; `toString` prints role, address, environment; no endpoint returns signers (`/api/v1/avalanche/signers` is 404); the repository contains no key literal | `keyMaterialIsNotReachableThroughThePublicSurface`, log scan in `enabledInLocalSimulationCreatesDistinctSignersAndLogsAddressesOnly`, source grep |
| Guards preserved; signer not constructible or usable in an unauthorized environment | factory needs a `ValidatedEnvironment`; the registry bean obtains it from the startup validator (so chain id and client family were verified first); `DevSigner` is package-private | `SignerStartupContextTest` |
| Signer separate from submission and business authorization | `Signer` signs one 32-byte digest and nothing else; it cannot build, send or authorize; it holds no contract or role capability | `signsOnlyExactly32ByteDigests`, interface review |
| Tests prove creation and signing rejected outside LOCAL_SIMULATION incl. Fuji | see first row; plus a Spring-context test: `avalanche.signers.enabled=true` with AVALANCHE_FUJI aborts startup with `SIGNER_FORBIDDEN_ENVIRONMENT` and **no RPC method was called** (mock verified `chainId()` and `clientVersion()` never invoked) | `SignerStartupContextTest.enablingSignersInFujiAbortsStartupBeforeAnyNetworkCall`; packaged jar: exit 1, no `validated` log line |
| Solana modules and Java 17 intact | no change to them | section 4 |

## 2. Signer lifecycle
1. Signers are **opt-in**: `avalanche.signers.enabled` (default false). When false there is no signer bean and no key is generated.
2. When true, the `signerRegistry` bean first checks the configured environment (before any network), then asks the startup validator for the verified environment (this runs the M1 chain id and client-family checks lazily, once), then creates **one fresh random key per role (8 roles: the 6 plan roles plus the 2 LOCAL_SIMULATION test wallets)**: AUTHORIZATION, ARBITER, RELAYER, PAUSER, ADMIN, DEPLOYER, TEST_BUYER, TEST_FREELANCER. The test roles are the LOCAL_SIMULATION backend development wallets approved in O-11.
3. Startup logs the role-to-address map (public data) once.
4. Keys exist only in process memory for the lifetime of the process. Restart means new keys and new addresses; nothing is rotated, exported or restored. Contract roles for these addresses are granted at deployment time in M7.
5. `sign(digest32)` re-checks environment and chain id each call, signs with web3j (deterministic RFC 6979), enforces **low-s** (a high-s result throws instead of being returned), and returns r, s, v (27 or 28).
M1 change made for this: `EnvironmentStartupValidator.validated()` is now idempotent and lazy (a failure is never cached as success); startup behaviour and M1 test results are unchanged.

## 3. Security boundaries
- Fuji and Mainnet can never obtain a signer: the refusal is repeated at config, factory, constructor and every signing call.
- A signer produces signatures over digests only. What to sign (business authorization), transaction building, nonces and sending are not in this package and arrive in M3 and later. No signer can move escrowed funds by itself: there is no contract, no role grant and no transaction code in M2.
- Roles have distinct keys, so compromising one role in the simulation does not yield another.
- Limitations unchanged from the Phase 1 evaluation: keys sit in process memory, no audit trail or policy limit, manual rotation, a compromised dev signer can act within its role on the local chain.

## 4. Test results
| Suite | Result |
|---|---|
| `avalanche-integration`, default build (`mvn -B -ntp -pl avalanche-integration test`, JDK 21) | **43 run, 41 passed, 0 failed, 2 skipped** (the optional Anvil and Fuji tests, off by default). M1 had 24; M2 added 19: `DevSignerFactoryTest` 6, `DevSignerTest` 9, `SignerStartupContextTest` 4 |
| Optional real Anvil test (`AVALANCHE_TEST_ANVIL_URL`) | 1/1 passed |
| Packaged jar, LOCAL_SIMULATION, real Anvil 1.8.7, signers enabled | started, 8 addresses logged, status endpoint live, no signer endpoint, working directory unchanged |
| Packaged jar, AVALANCHE_FUJI, signers enabled | exit 1 with `SIGNER_FORBIDDEN_ENVIRONMENT`; no validation line, i.e. no RPC contact |
| Solana baseline (same commands as the M1 report) | marketplace 6/6 plus gateway 10/10 = **16/16 combined**, identical to the M1 baseline and to the run after M1 (verified from the three Maven logs); reactor compile success; existing modules also compile on JDK 17 |
Signature properties proven: 450 random digests over 3 roles are low-s, v in {27, 28}, and recover to the signer address. EIP-712: the generic digest helper reproduces the specification's published Mail example digest (`0xbe609aee...7bd2`) and the published signature recovers to the published address; no key is stored in the repository.

## 5. Limitations and unresolved items
1. **Fuji deployment and any Fuji transaction have no backend signer** under the new owner rule. The Phase 2 plan ([05](05-environment-and-signer-safety-plan.md) section 4, [03](03-backend-implementation-plan.md) M2, [02](02-contract-implementation-plan.md) M7) still describes ephemeral Fuji signers with a funds cap; the owner's M2 instruction supersedes that text and the plan files were not edited. **Decision needed before M7 and the Fuji parts of M3 and M12:** how a Fuji deployment and Fuji smoke transactions are signed (for example a human-operated deployer outside the backend and the repository, or user wallets only). No policy was invented.
2. The plan's optional `LOCAL_FILE` signer source is not implemented because file persistence was not approved.
3. A contract-side EIP-712 digest equality vector and the escrow-specific typed structs (`Fund`, `Submit`, `Revision`, `MutualRefund`) wait for the contracts (M5/M6); M2 provides only the generic digest helper.
4. Transaction signing (EIP-155 raw transaction encoding bound to `Signer.chainId()`) and gas or fee caps belong to M3; the signer already exposes the verified chain id that M3 must use.
5. The compose service does not set `AVALANCHE_SIGNERS_ENABLED`; signers stay off unless explicitly enabled.
6. Phase 1 stays PARTIAL; its evidence is untouched.
