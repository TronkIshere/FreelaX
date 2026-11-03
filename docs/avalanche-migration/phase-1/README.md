# Phase 1 - Avalanche environment setup and technical experiments

> **Status note (2026-11-02):** Phase 2 (M1 to M12) has been executed and is IMPLEMENTATION COMPLETE WITH EXTERNAL VALIDATION PENDING. Statements in this folder that "Phase 2 has not started" are dated history. See [../PROJECT_STATUS.md](../PROJECT_STATUS.md).

> **Scope amendment (2026-11-01):** FreelaX is a development simulation with no real funds ([SIMULATION_SCOPE](../../SIMULATION_SCOPE.md)); Mainnet is out of scope. Mainnet probes in this phase were read-only and historical; no further Mainnet contact (including a Mainnet fork test) is planned. Guard evaluation: [simulation-environment-guards.md](simulation-environment-guards.md).

Executed **2026-11-01** against `master` (Phase 0 commit `1662a80`). Scope: environment and technical validation only. **Nothing in production code, configuration, schemas, Solana modules or dependencies was changed; nothing was committed or pushed; Phase 2 has not started.**

## Closure update (2026-11-03)
Owner response recorded in [../PROJECT_STATUS.md](../PROJECT_STATUS.md). Phase 1 stays **PARTIAL**; item-by-item tracking of
E1 to E7 is in the last section of [phase-1-acceptance-report.md](phase-1-acceptance-report.md). New: keyless human-signed Fuji
tooling ([prototypes/human-signed](prototypes/human-signed/README.md)) replacing the key-file runbook for Fuji (M2), the DB
operator output format in [db-rail-evidence-request.md](db-rail-evidence-request.md), and E7 desk evidence (ACP-283, FiatToken).

## Part B update (2026-11-01)
Part B closed every gap that did not need a funded Fuji wallet or database access. See [part-b-completion-report.md](part-b-completion-report.md) and the final section of [phase-1-acceptance-report.md](phase-1-acceptance-report.md). New since the first version: web-context and **Java 17** compatibility matrix (web3j 6.1.0 needs Java 21; Java 17 works with web3j 4.13.0), lifecycle prototype (8 scenarios), local fake-KMS signature conversion, restart-safe finalized-cursor scan validated against an independent provider, WebSocket reconnect behaviour, a **429 throttling episode on the official Fuji RPC**, a reusable guarded live-experiment script, and the operator requests for the two blocked items ([fuji-funding-runbook.md](fuji-funding-runbook.md), [db-rail-evidence-request.md](db-rail-evidence-request.md)). Status remains **PARTIAL** because of those two blockers.

## Final status: PARTIAL, technically viable with caveats
The proposed architecture (web3j + Foundry + OpenZeppelin on C-Chain, native USDC, direct log scanning, EIP-712 authorizations) was **not contradicted by any experiment**, and several Phase 0 assumptions were corrected by evidence. Phase 1's own acceptance criteria are **not fully met** because no funded Fuji wallet could be obtained (the faucets are interactive web flows), so no transaction of ours was ever executed on Fuji. See [phase-1-acceptance-report.md](phase-1-acceptance-report.md).

## Headline findings
1. **`eth_getLogs` on the official Fuji public RPC is capped at 100 blocks** (publicnode: 50,000, address required). The Phase 0 "2,000-block chunk" hypothesis is refuted; chunk size must be per-provider configuration, default 100.
2. **web3j 6.1.0 breaks at runtime on Spring Boot 3.5.16** (`NoClassDefFoundError JsonApplyView`) because it uses Jackson 3 and the Boot BOM pins `jackson-annotations` 2.21; an explicit `jackson-annotations:2.22` fixes it.
3. **Fuji USDC supports EIP-2612 permit and EIP-3009 `transferWithAuthorization`/`receiveWithAuthorization` behaviourally** (executed on a Fuji fork), not just by selector. USDC.e is distinguishable only by symbol and missing `DOMAIN_SEPARATOR` (both report name "USD Coin"; Phase 0 recorded "USDC Coin").
4. **Acceptance is not success** was proven locally (a reverted transaction is accepted by `eth_sendRawTransaction` and gets receipt status 0). On Fuji, accepted transactions had receipts on the first query and reached `finalized` 5.1-8.7 s after acceptance; `newHeads` fires essentially at acceptance. The accepted-but-unexecuted state could **not** be observed.
5. EIP-712: web3j and Foundry `cast` produce byte-identical signatures; the Java digest equals the contract digest; replay, expiry, wrong chain and wrong contract are all rejected.
6. Fuji currently runs **Cancun** (Pectra markers and Osaka absent). `block.timestamp` equals the header second (= `floor(timestampMilliseconds/1000)`) in all samples; blocks are irregularly spaced (up to ~20 s gaps in a 240-block sample) and several can share a second.

## Documents
| File | Content |
|---|---|
| [environment-setup.md](environment-setup.md) | Tool versions, layout, what could not be set up |
| [fuji-network-validation.md](fuji-network-validation.md) | RPC, limits, latency, rejection behaviour |
| [native-usdc-validation.md](native-usdc-validation.md) | Addresses, identity, permit/EIP-3009 behaviour |
| [java-web3j-evaluation.md](java-web3j-evaluation.md) | Java 21 / Boot 3.5.16 / web3j |
| [solidity-toolchain-evaluation.md](solidity-toolchain-evaluation.md) | Foundry vs Hardhat, OZ, EVM fork |
| [transaction-lifecycle-experiments.md](transaction-lifecycle-experiments.md) | Lifecycle and the backend state model |
| [eip712-experiments.md](eip712-experiments.md) | Typed signatures, interop, EIP-712 vs 2612 vs 3009 |
| [timestamp-deadline-experiments.md](timestamp-deadline-experiments.md) | Timestamp and deadline semantics |
| [rpc-reconciliation-evaluation.md](rpc-reconciliation-evaluation.md) | Scanning and reconciliation strategy |
| [security-key-management-evaluation.md](security-key-management-evaluation.md) | Signer architecture (analysis only) |
| [part-b-completion-report.md](part-b-completion-report.md) | Part B task status, technology baseline, decisions |
| [fuji-funding-runbook.md](fuji-funding-runbook.md) | Manual steps to fund an ephemeral Fuji wallet and run the blocked experiments |
| [db-rail-evidence-request.md](db-rail-evidence-request.md) | Read-only SQL request for a DB operator |
| [simulation-environment-guards.md](simulation-environment-guards.md) | Evaluation of startup/config guards for simulation-only operation |
| [phase-1-acceptance-report.md](phase-1-acceptance-report.md) | Acceptance table, blockers, recommendation |

Raw evidence: [evidence/](evidence/). Reproduce: `scripts/run-all.sh` (needs docker, node 22, JDK 21, Maven). Prototypes (isolated): `prototypes/solidity-foundry`, `prototypes/java-web3j`, `prototypes/ws-observer`.

## Evidence discipline
Every document separates **DOC** (official docs read), **OBS** (directly observed), **REC** (recommendation) and marks unverified items as UNVERIFIED/INCONCLUSIVE. Single-host, single-vantage Fuji observations are never presented as representative of all RPC providers. No benchmark comparing Avalanche to Solana or to other providers is claimed.

## Owner decisions after Part B
**Java 21**, **web3j 6.1.0** and **KMS deferred** (ephemeral development signers) are OWNER-DECIDED; see [part-b-completion-report.md](part-b-completion-report.md). Phase 1 remains **PARTIAL** (funded Fuji wallet and database evidence are still blocked). The Jackson overrides are research findings, not authorization to change production dependencies.
