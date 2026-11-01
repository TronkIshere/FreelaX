# 06. Implementation backlog (not started)

Ordered; none may begin before Phase 0 approval. Priority P0 critical path, P1 required, P2 optional. "Expected files" are proposals, not commitments. Phases: [04](04-migration-roadmap.md).

| # | Task | Phase | Depends on | Priority | Modules | Expected files | Acceptance |
|---|---|---|---|---|---|---|---|
| 1 | Re-verify all S/U facts (chain ids, USDC addresses/code, EVM version, RPC limits, `newHeads`) | 1 | approval | P0 | docs | `docs/avalanche-migration/environment.md` | every fact has two sources or "unverifiable" |
| 2 | Run ADR SQL evidence for rails (read-only, operator-run) | 1 | DB access | P0 | docs | evidence note | rail data mix known |
| 3 | Fuji wallets, AVAX, Circle test USDC | 1 | 1 | P0 | none | none | `balanceOf` works |
| 4 | Choose RPC providers and KMS vendor | 1 | - | P0 | docs | decision note | accounts exist |
| 5 | Foundry project skeleton with explicit `evm_version` | 1 | 1 | P0 | contracts | `avalanche-contracts/foundry.toml` | `forge build` passes |
| 6 | web3j 6.x dependency-tree check on Java 21 / Boot 3.5.16 | 1 | - | P0 | build | check note | no conflicts |
| 7 | `avalanche-integration` module skeleton + config validation | 2 | 1,6 | P0 | new module, root pom | module sources | module builds alone |
| 8 | Chain-id + asset validation | 2 | 7 | P0 | module | classes + tests | wrong chain/token rejected |
| 9 | Signer interface, dev signer, KMS signer | 2 | 4,7 | P0 | module | classes + tests | KMS signature recovers expected address |
| 10 | DB-backed nonce allocator + tx table | 2 | 7 | P0 | module | Flyway V1 | concurrent allocation test |
| 11 | Gas strategy, submit, receipt mapping, event decoding, error taxonomy | 2 | 7-10 | P0 | module | classes + tests | Fuji transfer e2e |
| 12 | Escrow contract design doc (state/rule mapping, roles, co-sign replacement) | 3 | decisions Q7-Q9 | P0 | contracts/docs | design doc | approved |
| 13 | Escrow contract + unit tests | 3 | 12,5 | P0 | contracts | `src/`, `test/` | all rules tested |
| 14 | Fuzz and invariant tests, Slither | 3 | 13 | P0 | contracts | tests | invariants hold |
| 15 | Deploy scripts + Fuji deployment + verification | 3 | 13 | P1 | contracts | `script/` | verified on explorer |
| 16 | Neutral port extension: escrow state/status enum, `TransactionReference` | 4 | 12 | P0 | marketplace | blockchain/* | Solana tests unchanged |
| 17 | Move `UnifiedUsdFundingService` fully behind port (incl. on-ramp strategy) | 4 | 16 | P0 | marketplace | service changes | existing tests pass |
| 18 | `AvalancheGatewayAdapter` + `AvalancheClient` | 4 | 11,16 | P0 | marketplace | adapter + tests | adapter tests |
| 19 | Additive Flyway migration (chain id, tx hash, nonce, block) | 4 | 16 | P0 | marketplace | migration V6+ | verified on DB copy |
| 20 | Log scanner + reconciliation on existing schedulers | 4 | 18,19 | P0 | marketplace/module | scanner | recovery test |
| 21 | Move `SolanaEscrow*`, `Contract*`, `PaymentFlowService` literals behind port | 4 | 16 | P1 | marketplace | service changes | regression |
| 22 | Parametrise MISA chain literal and explorer URL | 4 | 16 | P1 | marketplace | small edits | tests |
| 23 | Frontend neutral wallet interface + viem/wagmi adapter | 5 | 18 | P0 | frontend | wallet files | unit tests |
| 24 | Chain detection/switch, approve+fund UI, status | 5 | 23 | P0 | frontend | components | Fuji browser e2e |
| 25 | Fuji E2E matrix (fund, release, refund, dispute, claim) | 6 | 20,24 | P0 | tests | e2e scripts | all pass |
| 26 | Fault-injection suite (RPC outage, delayed execution, missing receipt, duplicates, nonce, revert) | 6 | 20 | P0 | tests | tests | all pass |
| 27 | Monitoring, alerts, runbooks, rollback drill | 6 | 25 | P0 | ops | runbooks | drill passed |
| 28 | External contract audit | 6 | 13 | P0 | external | report | findings resolved |
| 29 | Mainnet deploy, config, primary switch, rollback test | Final | 25-28 | P0 | all | runbooks | gates passed |

Parallelizable: 5/6 with 3/4; 7-11 with 12-15; 23 with 20-22 once 16 is frozen. Critical path: 1 -> 7 -> 11 -> 18 -> 20 -> 25 -> 28 -> 29.
