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

---
# Part B update (2026-11-01): Spring Boot web context, Jackson 2/3, Java 17

Tags: **OBS** directly observed (evidence files), **REC** recommendation. Environment: AVALANCHE_FUJI read-only calls (web matrix) and LOCAL_SIMULATION (full suite). Prototypes: `prototypes/java-web3j-web/` (minimal Spring Boot 3.5.16 web app: `@RestController` -> `@Service` -> Spring-managed `Web3j` bean -> JSON-RPC to Fuji, JSON served by Jackson 2) and `prototypes/java-web3j/`. Commands: `scripts/web_matrix.sh`, `scripts/java_matrix.sh`. The existing FreelaX Maven files were not touched.

## Existing FreelaX arrangement (read-only inspection of the root `pom.xml`)
Parent `spring-boot-starter-parent` 3.5.16, `java.version` **17**, and an explicit `jackson-bom.version` **2.21.7** (Boot's own default is 2.21.4). `jackson-annotations` therefore resolves to **2.21** in both cases.

## Web-context matrix (embedded Tomcat on a random port, real Fuji call through a Spring-managed bean)
| Java | web3j | Jackson arrangement | Result | Evidence |
|---|---|---|---|---|
| 21 | 6.1.0 | Boot BOM 2.21.4, no override (annotations 2.21) | **FAIL (reproduced)**: HTTP 500 from the controller, `NoClassDefFoundError ... JsonApplyView` during the first RPC | [java21-none.log](evidence/web-matrix/java21-none.log) |
| 21 | 6.1.0 | FreelaX pin `jackson-bom.version=2.21.7`, no override | **FAIL (reproduced)** same error | [java21-none-freelaxbom2.21.7.log](evidence/web-matrix/java21-none-freelaxbom2.21.7.log) |
| 21 | 6.1.0 | explicit `jackson-annotations:2.22` (databind 2 stays 2.21.4) | **PASS**: context starts, controller returns chain id 43113, head, finalized head | [java21-annotations22.log](evidence/web-matrix/java21-annotations22.log) |
| 21 | 6.1.0 | FreelaX pin 2.21.7 **plus** annotations 2.22 | **PASS** (the realistic fix for the current FreelaX pins) | [java21-annotations22-freelaxbom2.21.7.log](evidence/web-matrix/java21-annotations22-freelaxbom2.21.7.log) |
| 21 | 6.1.0 | `jackson-bom.version=2.22.4` (whole BOM aligned) | **PASS** (annotations, databind and core all 2.22.x) | [java21-bom22.log](evidence/web-matrix/java21-bom22.log) |
| **17** | 6.1.0 | any of the three | **FAIL at compile time**: `bad class file ... class file has wrong version 65.0, should be 61.0` | [java17-none.log](evidence/web-matrix/java17-none.log), [-annotations22](evidence/web-matrix/java17-annotations22.log), [-bom22](evidence/web-matrix/java17-bom22.log) |
| **17** | **4.13.0** | Boot BOM, no override | **PASS**: web context + real Fuji call, Jackson 2 only, okhttp 4.12.0 | [java17-none-web3j4.13.0.log](evidence/web-matrix/java17-none-web3j4.13.0.log) |
| 21 | 4.13.0 | Boot BOM, no override | **PASS** | [java21-none-web3j4.13.0.log](evidence/web-matrix/java21-none-web3j4.13.0.log) |

Dependency facts (OBS, `*.tree.txt` and class-version inspection of the published jars): web3j 6.1.0 core brings `tools.jackson:jackson-databind:3.2.3` (Jackson 3) + okhttp 5.5.0 and needs `jackson-annotations` 2.22. Jackson 2 (`com.fasterxml...databind` 2.21.x, used by Spring MVC) and Jackson 3 (`tools.jackson...`) have different packages and **coexist without conflict** in the same JVM; the only shared artifact is `jackson-annotations`, and annotations 2.22 with databind 2.21.x works (observed). **Java bytecode level of web3j artifacts (class major version, read from the published jars): 6.1.0, 6.0.0, 5.0.2, 5.0.3 and 4.14.0 are 65 (Java 21); 4.13.0 is 61 (Java 17).** web3j 4.13.0 uses Jackson 2 (declared 2.17.1, Boot raises it to 2.21.4) and okhttp 4.12.0; no override is needed.

## Full prototype suite across Java/web3j combinations (31 tests, 2 intentionally skipped live tests)
`scripts/java_matrix.sh` runs the whole suite (local anvil lifecycle, deploy, revert, nonce, duplicate, replacement, timeout, EIP-712, ERC-20 on a Fuji fork, Fuji read-only, lifecycle prototype, fake-KMS conversion, guard tests):
| Combination | Result | Evidence |
|---|---|---|
| Java 21 + web3j 6.1.0 (+ annotations 2.22) | **31 run, 0 failures, 2 skipped** | [evidence/java-matrix/java21-web3j6.1.0/](evidence/java-matrix/java21-web3j6.1.0/) |
| **Java 17 + web3j 4.13.0** | **31 run, 0 failures, 2 skipped** | [evidence/java-matrix/java17-web3j4.13.0/](evidence/java-matrix/java17-web3j4.13.0/) |
| Java 21 + web3j 4.13.0 | **31 run, 0 failures, 2 skipped** | [evidence/java-matrix/java21-web3j4.13.0/](evidence/java-matrix/java21-web3j4.13.0/) |
Test helpers were moved to Jackson 2 so one test source serves both web3j generations. One API difference found: in 6.1.0 `TransactionEncoder.encode(tx, SignatureData)` for a typed transaction needs `v` = 27/28 and writes yParity itself (4.13.0 behaved the same in the matrix).

## Conclusions
1. **web3j works reliably in a real Spring Boot web context** (embedded server, Spring-managed bean, real JSON-RPC) provided the Jackson arrangement is right. On Java 21 with web3j 6.1.0 that means the `jackson-annotations:2.22` override, which is compatible with FreelaX's own `jackson-bom.version` pin of 2.21.7. Without it the failure is a runtime HTTP 500 on the first RPC, not a build error.
2. **Java 17 and web3j 6.1.0 are incompatible** (bytecode 65). Java 17 is viable only with web3j 4.13.0 (or older): everything FreelaX needs from web3j in the prototype (EIP-1559 signing, ABI codec, EIP-712, receipt polling, log decoding) passed on Java 17 + 4.13.0.
3. **Decision for the owner (OPEN):** (a) new module on Java 21 + web3j 6.1.0 + annotations override, or (b) stay on Java 17 + web3j 4.13.0. REC: (a), because the project direction names Java 21 and 6.x is the maintained line; (b) is a sound fallback with no override. The existing FreelaX Java level was not changed.
4. Limits: web matrix used one host and one run per cell; Boot's `spring-boot-starter-actuator`/security/Jackson-module interplay was not exercised; web3j 4.13.0 maintenance status and CVE posture were not assessed.
