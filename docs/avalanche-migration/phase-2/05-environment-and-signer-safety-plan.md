# 05. Environment isolation and signer safety plan (plan only)

Specification source: [simulation-environment-guards.md](../phase-1/simulation-environment-guards.md) (with Part B refinements) and [security evaluation](../phase-1/security-key-management-evaluation.md). Nothing is implemented here. Owner decision: **KMS deferred; ephemeral development signers**.

## 1. Environments
| | LOCAL_SIMULATION | AVALANCHE_FUJI |
|---|---|---|
| Network | Anvil (Docker, pinned 1.8.7) or another documented local EVM; optionally a **local fork** of Fuji | Avalanche Fuji C-Chain, chain id **43113** |
| Chain id | 31337 (plain Anvil); a local Fuji fork reports **43113** and is accepted **only** on a loopback endpoint | 43113 |
| Assets | `MockUSDC` (simulated, no value) | Circle testnet USDC; testnet AVAX for gas |
| Transactions | local, deterministic | genuine public-testnet transactions; **not** financial settlement |
| Finality policy | confirmation depth (the `finalized` tag did not advance on Anvil in Phase 1) | `finalized` tag (about 4-5 blocks behind latest; 5 to 9 s after acceptance observed) |
| Client-version family | `anvil/` | `avalanchego/` |
| **Mainnet (43114)** | **never** | **never** |

## 2. Selection and validation (fail closed, in this order)
1. `avalanche.environment` must be set explicitly; there is **no default** and no implicit switch between environments.
2. **Pre-network endpoint check** (no request is sent yet): reject Mainnet RPC hosts (for example `api.avax.network`), reject loopback for AVALANCHE_FUJI, reject non-loopback for LOCAL_SIMULATION.
3. `eth_chainId` read from the endpoint: reject 43114 and 1 with a dedicated error, reject any chain id not allow-listed for the environment. The allow-list is **compiled into the code**, not configuration; changing it is a code change.
4. `web3_clientVersion` family check as **supporting evidence** (a local fork can be told apart from real Fuji: `anvil/v1.8.7` vs `avalanchego/1.15.2` in Phase 1), never the only barrier.
5. Asset check by `(chainId, address)` from the deployment manifest or the Fuji allow-list; `decimals()==6`, symbol, version where applicable; **deny-list** containing the Mainnet native USDC address and the USDC.e shape (symbol `USDC.e`, no `DOMAIN_SEPARATOR`) with dedicated errors so a pasted Mainnet address fails loudly.
6. Secondary RPC provider: must report the **same** chain id and client family and be configured for the **same** environment; no fallback across environments, ever; a throttled provider (HTTP 429) is never a reason to change environment.
7. Signer checks (section 4) and record isolation (section 5).
Any failure stops startup (`fail closed`): no "warn and continue".

## 3. Contract and tooling guards (defence in depth)
Deploy script reverts unless `block.chainid` is 31337 or 43113 (43114 rejected explicitly); manifests carry `environment` and `chainId`; the gateway refuses a manifest whose chain id does not match the live endpoint; test and CI configuration never contains Mainnet URLs (a CI grep check is part of M1).

## 4. Signers
| Rule | Detail |
|---|---|
| One key per role and environment | roles: authorization signer, arbiter, relayer, pauser, administrator, deployer; plus test buyer and freelancer wallets in LOCAL_SIMULATION |
| Ephemeral | generated per environment/run in memory; Fuji wallets are single-purpose |
| No silent persistence | a file source exists only if explicitly configured (path outside the repository, announced at startup, only the address logged); the Phase 1 live script's `generate` mode is the model (it prints the location and the address, never the key) |
| No reuse | never a personal or production key; no import of existing wallets or seed phrases |
| Funds cap | refuse a Fuji signer whose balance is above a testnet-scale threshold (safety net; not proof) |
| Mainnet impossible | no signer can be built for a chain id outside the allow-list; signing helpers take the validated chain id, never one from a request |
| Interface | `sign(digest32) -> (r, s, v)` with low-s normalisation and recovery-id search (validated locally in Phase 1); a KMS implementation can be added later without touching business code |
Limitations are explicit and must be repeated in operator documentation: keys sit in process memory or a plain file; there is no audit trail or policy limit; rotation is manual (grant new, revoke old on-chain); a compromised dev signer can authorize within its role on the testnet. A future production-grade design (KMS/HSM, ceremony, multisig, audit logging) is **not** planned and needs a separate owner decision.

## 5. Record metadata and labelling
Every persisted chain record (transaction attempt, cursor, event, escrow reference, manifest) carries `environment` and `chain_id`; API responses add `environment`, `isSimulated` and an explorer URL only for Fuji. The reconciler ignores rows of another environment (Phase 1 S8). Simulated financial records (fees, tax, payout figures, balances) are stored and exposed separately from on-chain transaction records and are labelled "simulated" ([04](04-frontend-integration-plan.md)).

## 6. Test requirements (all feasible without funds)
Startup refuses: missing environment; chain ids 43114, 1, 0 and an unlisted id; Mainnet endpoint host **without any request**; LOCAL_SIMULATION pointed at a public URL and AVALANCHE_FUJI at localhost; a secondary provider with another chain id; a deny-listed asset address; USDC.e shape; a manifest for another chain. A local Fuji fork is accepted only as LOCAL_SIMULATION. Signing helper refuses a chain id different from the validated one. Records of another environment are ignored. Log scan shows no key material. Phase 1 already exercised the endpoint pre-check, environment/endpoint mismatch, fork-as-local and record isolation in a research prototype; the rest are new acceptance tests for P2-M1/M2.

## 7. Milestone placement
Guards: P2-M1. Signers: P2-M2. Manifests and deploy guard: P2-M7. Labelling: P2-M9 (backend) and P2-M11 (frontend).
