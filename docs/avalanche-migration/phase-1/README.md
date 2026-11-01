# Phase 1 - Avalanche environment setup and technical experiments

> **Scope amendment (2026-11-01):** FreelaX is a development simulation with no real funds ([SIMULATION_SCOPE](../../SIMULATION_SCOPE.md)); Mainnet is out of scope. Mainnet probes in this phase were read-only and historical; no further Mainnet contact (including a Mainnet fork test) is planned. Guard evaluation: [simulation-environment-guards.md](simulation-environment-guards.md).

Executed **2026-11-01** against `master` (Phase 0 commit `1662a80`). Scope: environment and technical validation only. **Nothing in production code, configuration, schemas, Solana modules or dependencies was changed; nothing was committed or pushed; Phase 2 has not started.**

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
| [simulation-environment-guards.md](simulation-environment-guards.md) | Evaluation of startup/config guards for simulation-only operation |
| [phase-1-acceptance-report.md](phase-1-acceptance-report.md) | Acceptance table, blockers, recommendation |

Raw evidence: [evidence/](evidence/). Reproduce: `scripts/run-all.sh` (needs docker, node 22, JDK 21, Maven). Prototypes (isolated): `prototypes/solidity-foundry`, `prototypes/java-web3j`, `prototypes/ws-observer`.

## Evidence discipline
Every document separates **DOC** (official docs read), **OBS** (directly observed), **REC** (recommendation) and marks unverified items as UNVERIFIED/INCONCLUSIVE. Single-host, single-vantage Fuji observations are never presented as representative of all RPC providers. No benchmark comparing Avalanche to Solana or to other providers is claimed.
