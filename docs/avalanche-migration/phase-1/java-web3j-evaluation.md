# Phase 1 - Java 21 / Spring Boot 3.5.16 / web3j evaluation

Prototype: `prototypes/java-web3j/` (standalone Maven project, parent `spring-boot-starter-parent:3.5.16`, not a FreelaX module). Evidence: [web3j-dependency-tree.txt](evidence/web3j-dependency-tree.txt), [java-local-experiments.json](evidence/java-local-experiments.json), [java-fuji-fork-experiments.json](evidence/java-fuji-fork-experiments.json). Build and run: JDK 21.0.2, Maven 3.9.16, web3j **6.1.0**.

## Build and test result
`mvn test`: **14 tests, 0 failures** (`LocalEvmExperimentsTest` 10 against local anvil chain 31337; `FujiAndForkTest` 4 against real Fuji read-only + a Fuji fork). Experiments are recorded in the evidence JSON, not merely compiled.

## Dependency tree (OBS)
`org.web3j:core/abi/crypto/rlp:6.1.0`, `com.squareup.okhttp3:okhttp:5.5.0` (+ `okhttp-jvm`, `logging-interceptor`), `org.jetbrains.kotlin:kotlin-stdlib:1.9.25`, `io.reactivex.rxjava2:rxjava:2.2.21`, `org.bouncycastle:bcprov-jdk18on:1.86`, `org.slf4j:slf4j-api:2.0.18`, and **`tools.jackson.core:jackson-databind:3.2.3` (Jackson 3)**. Spring Boot 3.5.16 manages `jackson-bom` **2.21.4** (Jackson 2, `com.fasterxml`). Full tree in the evidence file (142 lines).

## Known incompatibility (REAL, runtime-only)
**web3j 6.1.0 + Spring Boot 3.5.16 BOM fails at the first RPC call** with `java.lang.NoClassDefFoundError: com/fasterxml/jackson/annotation/JsonApplyView`. Cause: Jackson 3.2.3 needs `jackson-annotations` **2.22** (class `JsonApplyView`), while Boot's BOM pins annotations to **2.21**. Compilation and Spring context startup succeed; every `web3j.*.send()` fails. Evidence: first test run (10/10 errors) before the fix.
**Workaround verified:** declare `com.fasterxml.jackson.core:jackson-annotations:2.22` explicitly in the module pom (explicit version beats the BOM). With it, all experiments pass.
Remaining risk (not tested): in a module that also has Jackson 2 `jackson-databind` 2.21.4 (e.g. `spring-boot-starter-web`), annotations 2.22 with databind 2.21.4 should be backward compatible but was **not exercised**; Phase 2 must run a web-context test. Jackson 2 and Jackson 3 use different packages (`com.fasterxml.jackson.databind` vs `tools.jackson.databind`), so classes do not clash.
Because the failure is runtime-only, **Phase 2 CI must contain a smoke test that performs a real web3j call**; "it builds" is not evidence.

## Java level
Prototype compiled and ran on **Java 21**. The existing project pins `java.version` 17; web3j 6.1.0 on 17 was **not tested**. The `JAVA_HOME=jdk-20` machine setting broke the Maven build until overridden ([environment-setup.md](environment-setup.md)).

## Capability checklist (directive section 6)
| # | Item | Result |
|---|---|---|
| 1 | Spring Boot application startup (`@SpringBootTest`, Web3j `@Bean` with OkHttp timeouts) | PASS (3.5.16, Java 21.0.2) |
| 2 | JSON-RPC connection, chain id | PASS (local 31337; Fuji 43113 on two providers) |
| 3 | Latest block | PASS |
| 4 | Native balance | PASS |
| 5 | ERC-20 interaction (USDC on Fuji fork: balanceOf, approve, allowance, transfer revert) | PASS |
| 6 | ABI encode/decode (`FunctionEncoder`, `FunctionReturnDecoder`, custom `Event`) | PASS |
| 7 | Transaction construction (EIP-1559 `RawTransaction`) | PASS |
| 8 | Local signing with ephemeral key (`TransactionEncoder.signMessage(raw, chainId, creds)`) | PASS; locally computed `keccak256(signedBytes)` **equals** the node's returned hash, so the hash can be persisted before sending |
| 9 | Raw signed tx submission | PASS (anvil, fork) |
| 10 | Receipt polling | PASS (hand-written poll loop; no framework needed) |
| 11 | Revert detection | PASS: receipt `status 0x0` with explicit gas limit; `eth_call`/`estimateGas` show `execution reverted`; **custom errors need manual selector decoding** (`getRevertReason()` handles only `Error(string)`) |
| 12 | Event/log decoding (indexed + non-indexed) | PASS (ValueSet, Acted, Approval, Transfer) |
| 13 | Nonce handling | PASS as an *experiment*; shows web3j gives no safety: two readers of `pending` nonce collide (J10 `nonce too low`). DB-backed allocator required. |
| 14 | Error handling and timeouts | PASS: see below |
| 15 | EIP-712 sign/recover | PASS ([eip712-experiments.md](eip712-experiments.md)) |
| - | Revert-case reconciliation, replacement, duplicate | see [transaction-lifecycle-experiments.md](transaction-lifecycle-experiments.md) |

### Error classes observed (needed for the definitive vs non-definitive taxonomy)
| Situation | What web3j surfaces |
|---|---|
| JSON-RPC error on send (`nonce too low`, `insufficient funds`, `replacement transaction underpriced`, `transaction already imported`, `invalid sender`) | `EthSendTransaction.hasError()`; **no exception**. Caller must check. |
| Read timeout / call timeout | `SocketTimeoutException` or `InterruptedIOException` ("timeout"), depending on which OkHttp timer fires: treat both as **non-definitive** |
| HTTP 503 from a proxy | `ClientConnectionException` (non-definitive) |
| Connect timeout / refused | `SocketTimeoutException` / `ConnectException` (non-definitive; request never sent) |
| Unknown tx/receipt | `Optional.empty` (not an error) |

## Direct JSON-RPC comparison
`Rpc.java` (java.net.http + Jackson 3) was used for anvil admin methods and to see raw error bodies. For read calls and simple sends it is equally capable. It offers **no** ABI codec, signing, or EIP-712, which are the substantive parts. **Raw JSON-RPC does not replace web3j** here; it is a useful complement for diagnostics. No second library was evaluated because web3j proved sufficient.

## Reuse of existing FreelaX scheduling (read-only inspection)
`marketplace-backend` has `@EnableScheduling` and about 14 `@Scheduled` fixed-delay jobs with property-driven delays (for example `PaymentFlowService`, `UnifiedUsdFundingService`, `SolanaEscrowTimeoutScheduler`, `PayoutReconciliationScheduler`). A grep found no ShedLock or similar lock annotation: whether multi-instance duplicate runs are handled is **not established by this phase**. Assessment: receipt/log reconciliation fits the same pattern (a configurable fixed-delay job reading persisted tx records), so a second polling framework is **not** needed. Nothing was changed.

## Recommendation
1. **Use web3j 6.1.0** as the ABI / signing / JSON-RPC layer inside a new `avalanche-integration` module, with: explicit `jackson-annotations:2.22` (re-check on every Boot or web3j bump), OkHttp timeouts set explicitly, the error taxonomy above, and a CI smoke test with a real call.
2. Do **not** use `FastRawTransactionManager` or in-memory nonces; allocate nonces from the database per signer.
3. Persist the locally computed tx hash and raw tx **before** sending (verified feasible).
4. Decide the module Java level in Phase 2 (17 vs 21); only 21 was verified.
5. No alternative library is needed. If Jackson coexistence proves painful in the web context, the fallback is to isolate web3j in its own module/classloader boundary before replacing it.

## Status
Java 21 + Boot 3.5.16 + web3j 6.1.0 compatibility: **PASS with one mandatory workaround**. Java 17 compatibility and Boot-web coexistence: **INCONCLUSIVE**.
