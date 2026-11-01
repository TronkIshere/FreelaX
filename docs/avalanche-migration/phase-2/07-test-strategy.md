# 07. Test strategy (plan only)

Principle from Phase 1: "it builds" is not evidence. Runtime-only failures (the web3j/Jackson `NoClassDefFoundError`) were found only by executing real calls, so every layer below includes executed behaviour. Local-simulation results are never reported as Fuji behaviour.

## 1. Layers
| Layer | Tooling | What it proves | Environment |
|---|---|---|---|
| Contract unit | Foundry | one test per mapping row, exact timing boundaries (both sides), bounds, role negatives, EIP-712 negatives (chain, contract, escrow, action, nonce, deadline, expiry, replay), pause blocks only `fund` | local EVM |
| Contract fuzz and invariants | Foundry | sum of open amounts <= balance; no payout above deposit; settled escrow cannot settle again; no cross-escrow authorization; dispute isolation; terminal finality; direct sends not claimable | local EVM |
| Contract fork | Foundry `--fork-url` | USDC interaction on the real Fuji token (`approve` then `fund`, optional permit/EIP-3009), blacklist/pause behaviour via impersonated issuer roles | local fork of Fuji (loopback; reports 43113) |
| Static analysis | Slither 0.11.6 | triaged findings (timestamp comparisons are intentional) | CI |
| Backend unit | JUnit 5 | state machine, error classifier, nonce allocator logic, guards, signer normalisation, provider profiles | none |
| Backend integration | JUnit + Anvil (Docker) + MySQL (compose or Testcontainers) | scenarios S1-S8 with the real DB, concurrency (unique nonces), restart recovery, scanner crash/restart equality, web-context smoke test with a real web3j call | LOCAL_SIMULATION |
| Fault injection | local proxy in front of Anvil | hold response, null lookups, HTTP 429 with HTML and `Retry-After`, 503, duplicate WebSocket subscription | LOCAL_SIMULATION |
| Adapter/regression | existing suites | **all Solana tests pass unchanged**; adapter tests mirror `SolanaGatewayAdapterTest`; rail regression matrix | none |
| Migration | copy of a database | additive Flyway migration, legacy rows unaffected | copy only |
| Frontend | Vitest (existing) | Solana wallet tests unchanged; EVM adapter with mocked EIP-1193; wrong-network and refused-chain (43114); terminology and labelling | none |
| Browser e2e | Playwright or equivalent (to be chosen) | fund, release, refund, dispute on Anvil with an injected wallet or the local dev-wallet option | LOCAL_SIMULATION |
| Fuji smoke | live script (`LiveExperimentsTest` pattern) | own-transaction timing, revert, nonce gap/replacement threshold, timeout, duplicate, near-deadline inclusion, deployment | AVALANCHE_FUJI, **BLOCKED until a funded wallet exists** |
| Guard tests | JUnit/Spring | every failing configuration in [05](05-environment-and-signer-safety-plan.md) section 6 | none/Anvil |
| Secret hygiene | CI grep + log scan | no keys, `.env`, Mainnet URLs or addresses in the repository or logs | CI |

## 2. Gates per milestone
M1: guard tests and web-context smoke test green. M2: signer tests green, log scan clean. M3: S1-S8 and concurrency green. M4: scanner restart equality green, fault injection green. M5/M6: all mapping rows, invariants and negatives green, Slither triaged. M7: Anvil deployment reproducible, deploy-script chain guard tested (43113 and 31337 accepted, 43114 rejected). M8/M9: Solana regression unchanged, adapter and integration green, migration copy test. M10/M11: Vitest green, Anvil browser flow, terminology review. M12: Anvil end-to-end matrix (fund, release, refund, dispute, claim after deadline, extension, revision, mutual refund), release with the backend stopped, fault injection suite; Fuji smoke when funded.

## 3. Rules
No test sends a transaction to Fuji without an explicit flag and the funding guard; no test contacts Mainnet (CI fails on Mainnet hosts in configuration); tests use ephemeral keys only; evidence files contain addresses and hashes, never keys; public endpoints are used at a conservative pace with `Retry-After` honoured; flaky public-RPC tests are marked and excluded from the default build.
