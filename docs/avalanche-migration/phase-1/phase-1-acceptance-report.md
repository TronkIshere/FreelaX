# Phase 1 - Acceptance report

> **Scope amendment (2026-11-01):** FreelaX is a development simulation with no real funds ([SIMULATION_SCOPE](../../SIMULATION_SCOPE.md)); Mainnet is out of scope. Mainnet probes in this phase were read-only and historical; no further Mainnet contact (including a Mainnet fork test) is planned. Guard evaluation: [simulation-environment-guards.md](simulation-environment-guards.md).

Date: 2026-11-01. Base: `master` @ `1662a80`. Status keys: **PASS** (evidence recorded), **PARTIAL**, **INCONCLUSIVE** (could not be established), **FAIL**, **BLOCKED** (needs something unavailable).
**Overall: PARTIAL. Recommendation: resolve a short list of blockers before Phase 2** (section 6).

## 1. Acceptance criteria
### 1a. Roadmap Phase 1 criterion ([04](../04-migration-roadmap.md), Phase 1 item 13) - the stricter, original criteria
| Criterion | Status | Evidence / reason |
|---|---|---|
| All facts verified from two sources or marked unverifiable | **PARTIAL** | Chain ids, RPC, USDC identity/behaviour, EVM fork, log limits, `newHeads`: verified by observation (+ docs where they exist). USDC address has **one** official source (Circle). Unverified and marked: rate limits, USDC.e official address, Mainnet EVM/permit behaviour, ACP-283 status, FiatToken version, explorer verification flow, faucet limits |
| Faucet-funded wallet can call USDC `balanceOf` | **FAIL / BLOCKED** | No funded wallet (interactive faucets). `balanceOf` works on any address (read-only, real Fuji, via web3j and Foundry). |
| Fuji: time from submission to receipt and to `finalized` | **INCONCLUSIVE (own tx)** | Measured for others' txs only: acceptance -> receipt <= 1.2 s incl. our calls; acceptance -> finalized 5.1-8.7 s ([lifecycle](transaction-lifecycle-experiments.md)) |
| Fuji: `eth_getLogs` before execution | **INCONCLUSIVE** | Same-filter results before/after receipt identical 14/14, but the early query may already have been post-execution |
| Fuji: accepted-but-unexecuted visible via `eth_getTransactionByHash` | **INCONCLUSIVE** | Not observed; docs say receipt reads wait for execution; one by-hash miss in 53 |
| Fuji: `block.timestamp` semantics | **PARTIAL** | Header/ms/opcode-replay measured; near-deadline inclusion and original execution not testable without funds |
| No signer or key-management code written in this phase | **PASS with documented discrepancy** | No production signer, KMS client, role or key store exists. The **directive required** local signing with ephemeral keys, so test-only signing code exists in the isolated prototypes (web3j `Credentials`; two trivially-guessable Foundry test keys `0x5151`, `0xA11CE5` used only with `vm.sign`). The stricter reading of the roadmap is met in spirit (no signer module) and the discrepancy is recorded here. |

### 1b. Directive objectives
| # | Objective | Status |
|---|---|---|
| 1 | Isolated Avalanche dev environment | **PASS** (Docker-pinned Foundry; all under `phase-1/`) |
| 2 | Fuji connectivity and network config | **PASS** |
| 3 | Native USDC contract info | **PASS** (Fuji; Mainnet read-only; USDC.e official address unverified) |
| 4 | Java 21 / Boot 3.5.16 / web3j | **PASS with mandatory workaround**; Java 17 and Boot-web coexistence INCONCLUSIVE |
| 5 | Solidity, Foundry, OpenZeppelin tooling | **PASS** (Hardhat not run; Fuji deployment and explorer verification not done) |
| 6 | Minimal standalone prototypes | **PASS** (14 Java tests, 13 Foundry tests, 3 Node probes) |
| 7 | EVM transaction lifecycle incl. accepted-but-unexecuted | **PARTIAL**: client/EVM behaviour PASS on anvil; Helicon state INCONCLUSIVE |
| 8 | EIP-712 and domain separation | **PASS** (wallet-side signing and KMS UNVERIFIED) |
| 9 | `block.timestamp` and deadlines | **PARTIAL** |
| 10 | RPC reliability, limits, logs, reconciliation | **PASS** for limits/strategy; rate limits, WS reconnect, duplicate delivery INCONCLUSIVE |
| 11 | Reproducible evidence | **PASS** (`scripts/run-all.sh`, `evidence/`) |
| 12 | Documentation and honest backlog statuses | **PASS** ([06](../06-implementation-backlog.md) updated: 5 and 6 done, 1 and 7 partial, 2, 3, 4 not done) |

## 2. Failed and inconclusive experiments (complete list)
| Item | Type | Reason |
|---|---|---|
| Phase 0 hypothesis: 2,000-block log chunks | **FAILED (hypothesis)** | official Fuji RPC allows 100 |
| Phase 0 claim: USDC `name()` is "USDC Coin" | **FAILED (fact)** | actual "USD Coin" |
| web3j 6.1.0 out-of-the-box on Boot 3.5.16 | **FAILED, fixed** | Jackson 3 vs annotations 2.21 |
| Own Fuji transactions (success, revert, nonce gap, replacement, near-deadline, timings) | BLOCKED | no funded wallet |
| Accepted-but-unexecuted observation | INCONCLUSIVE | see lifecycle L3 |
| `eth_getLogs` before execution | INCONCLUSIVE | observation window |
| Fuji deployment of the lab contract, explorer verification | NOT PERFORMED | no funds; not required for any question |
| Replacement fee-bump factor on Avalanche | INCONCLUSIVE | measured on anvil only (3x worked) |
| Public RPC rate limits, WS reconnect/duplicates, historical depth | INCONCLUSIVE | not stress-tested |
| Mainnet permit/EIP-3009/EVM-fork behaviour, USDC blacklist/pause writes | UNVERIFIED | Mainnet probes were read-only; privileged roles unavailable |
| KMS signing, Vault, wallet-side `eth_signTypedData_v4` | UNVERIFIED | no accounts / no browser wallet |
| `cast hash-typed-data` digest compare | script error | covered indirectly by identical signature |
| Slither on the real escrow, fuzz/invariants | NOT APPLICABLE | Phase 3 |
| Read-only SQL rail evidence (backlog 2) | NOT DONE | needs DB operator |
| Java 17 with web3j 6.1.0; Boot web-context Jackson coexistence | INCONCLUSIVE | not run |

## 3. Remaining technical unknowns
Public-endpoint rate limits; behaviour of a Helicon-accepted transaction before execution; what an *original* asynchronous execution sees as `block.timestamp`; replacement bump rule on Avalanche; Mainnet-specific behaviour (EVM fork, USDC implementation); KMS signature conversion; Jackson 2.22 + databind 2.21 in a web context; WebSocket reconnect/duplicate semantics; web3j wrapper generation.

## 4. Remaining business/architecture decisions (not closed by this phase)
All items in [07](../07-open-questions.md) questions 1-18 remain as recorded (Phase 0 decision register D1-D4 untouched): arbiter model, revision co-sign, 72h parameter, escrow ambiguities, USDC blacklist payout, mutual refund mechanism, gas payer, multisig membership, rail literal, legacy payout path, in-flight Solana escrows, on-ramp. New items surfaced: module Java level (17 vs 21), RPC provider(s), KMS vendor, whether to accept the 100-block scanning default or buy a provider with larger ranges.

## 5. Safety and scope verification
- Tracked files modified: only [04](../04-migration-roadmap.md), [06](../06-implementation-backlog.md), [07](../07-open-questions.md) (additive status text; decision register untouched). All other changes are new files under `docs/avalanche-migration/phase-1/`.
- No production source, configuration, Flyway migration, dependency, Solana, frontend or auth file was modified. `.idea/` and `.serena/` untouched. No commit, no push.
- No private key or secret in files or logs (scan: only bytecode, public constants, digests, signatures; the EIP-712 interop key lived in the OS temp dir and was deleted). One-time nested `.git` accidentally created by `git init` in the Foundry prototype was removed.
- No mainnet writes; no Fuji writes except one signed transaction from an unfunded ephemeral key that the node rejected (nothing executed, nothing spent).
- Build artefacts (`lib/`, `out/`, `cache/`, `target/`) are git-ignored by local `.gitignore` files.
- Documentation links checked (0 broken). Disposable Docker containers removed.

## 6. Recommendation
**Resolve blockers first, then proceed to Phase 2.** Phase 2 module work (RPC client, nonce allocator, receipt mapping) is not technically blocked, but its acceptance depends on facts Phase 1 could not measure. Minimum list, estimated as a short session:
1. Obtain Fuji AVAX and Circle test USDC for one ephemeral wallet (interactive faucets; needs the operator) and run the blocked Fuji experiments: submit -> receipt -> finalized timings, revert, nonce gap/replacement bump, deadline-boundary calls with included block timestamps, accepted-state observation with own hashes. A guarded live test class can reuse the existing prototype helpers.
2. Decide the module Java level and confirm web3j under a Boot web context with the Jackson override.
3. Choose RPC provider(s) and KMS vendor (decisions, not experiments).
4. Optionally repeat the USDC fork test against a Mainnet fork.
Phase 2 must also keep a CI smoke test that performs a real web3j call.
**Stop here: Phase 2 is not started; explicit approval is required.**
