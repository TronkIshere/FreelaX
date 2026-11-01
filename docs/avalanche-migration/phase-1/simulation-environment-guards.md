# Simulation-only environment guards: evaluation for future implementation

Documentation only. Nothing here is implemented; no production code or configuration was changed. Scope: [SIMULATION_SCOPE.md](../../SIMULATION_SCOPE.md). Facts below are tagged **OBS** (observed in Phase 1, with evidence file), **DOC**, or **REC** (recommendation for Phase 2+).

## G1. What Phase 1 shows about identifying an environment
| Finding | Evidence | Consequence |
|---|---|---|
| A **local fork of Fuji reports chain id 43113** on `localhost:8546` | [java-fuji-fork-experiments.json](evidence/java-fuji-fork-experiments.json) J14 (`forkChainId=43113`) | **Chain id alone cannot tell LOCAL_SIMULATION-fork from real AVALANCHE_FUJI.** The environment must be an explicit setting, cross-checked against the endpoint, never inferred from chain id. |
| Plain anvil reports 31337 | J1 | 31337 is unambiguous for a non-fork local chain |
| Fuji public node reports `web3_clientVersion` = `avalanchego/1.15.2 ...` (2026-11-01) | direct call this session | A client-version check is a usable secondary signal (a local anvil would report an anvil version: **not measured**; verify in Phase 2) |
| EIP-155 binding works: a transaction signed for chain id 1 is rejected by Fuji with `invalid sender` | J13 | Signing with the allow-listed chain id gives a second, protocol-level barrier against cross-network replay |
| Unfunded transactions are rejected at admission | J13 | Not a guard we control, but confirms nothing can execute without testnet funds |
| web3j `Credentials`/`TransactionEncoder` take the chain id as an explicit argument | prototype code | The chain id used for signing can be forced from the validated configuration, not from the RPC |

## G2. Recommended guard set (for Phase 2 acceptance criteria)
1. **Explicit environment setting:** `freelax.chain.environment` is one of `LOCAL_SIMULATION`, `AVALANCHE_FUJI`; no default; absence is a startup failure (no silent fallback, no implicit switch).
2. **Compiled allow-list, not configuration:** the mapping `environment -> (chainId, address allow-list, endpoint class)` lives in code. LOCAL_SIMULATION: chain id 31337 (or 43113 only when the endpoint is loopback/private and the environment is LOCAL_SIMULATION), AVALANCHE_FUJI: 43113 and a non-loopback endpoint. **Chain ids 43114 (Mainnet) and 1 and everything else are rejected with a dedicated error.** There is no property that adds chain ids.
3. **Endpoint cross-check:** reject any endpoint host that is a known Mainnet RPC; reject a loopback endpoint when the environment is AVALANCHE_FUJI and a public endpoint when it is LOCAL_SIMULATION; check `eth_chainId` at startup and on every client (including fallback providers, which must be in the **same** environment).
4. **Asset allow-list by `(chainId, address)`:** Fuji: Circle's Fuji USDC (re-verified 2026-11-01, see [native-usdc-validation.md](native-usdc-validation.md)); local: the deployed simulated USDC address recorded in a deployment manifest. The Mainnet native USDC and USDC.e addresses are placed in a **deny-list** with a dedicated error so that a pasted Mainnet address fails loudly. Startup assertions: `decimals()==6`, symbol, EIP-712 version where applicable.
5. **Signer guard:** only development signers (local keystore outside the repo, or test mnemonic) are allowed; refuse a key source that looks like a production secret store alias; refuse to start if the signer holds more than a configured testnet-scale balance on Fuji (a heuristic against a mistaken real-key import; it cannot detect real funds on other chains and is a guard, not a proof). KMS is **not required** by the simulation scope (open question for the owner: D3 principles stay, KMS vendor choice may be dropped).
6. **Record isolation:** every persisted chain record (transaction attempt, scan cursor, escrow reference) carries `environment` and `chain_id`; the reconciler refuses rows whose environment differs from the running one; the scan-cursor key includes `(environment, chain_id, contract)`.
7. **Fail closed:** configuration validation runs in an application-start hook (`@ConfigurationProperties` validation plus a startup runner); on any violation the context does not start. No "warn and continue".
8. **No secrets in Git:** secret scanning of the repo in CI (Phase 1 manual scan found none); keys only from a local ignored file or environment, never committed or logged; RPC URLs redacted in logs.
9. **Labelling in the API and UI:** responses carry `environment`, `isSimulated`, `explorerUrl` (testnet explorer for Fuji, none for local); UI badges per [SIMULATION_SCOPE.md](../../SIMULATION_SCOPE.md) section 7.

## G3. Tests to require (all feasible without funds, using what Phase 1 built)
- Startup refuses chain id 43114, 1, 0, and an unlisted id (use anvil with `--chain-id`).
- Startup refuses LOCAL_SIMULATION pointed at the public Fuji URL and AVALANCHE_FUJI pointed at localhost.
- A local Fuji **fork** accepted only as LOCAL_SIMULATION (the J14 setup).
- Deny-listed Mainnet USDC address rejected; USDC.e shape (symbol `USDC.e`, no `DOMAIN_SEPARATOR`) rejected.
- A fallback provider with a different chain id is rejected.
- Records from another environment are ignored by the reconciler.
- Signing helper refuses a chain id that differs from the validated one.

## G4. Limits of this evaluation
No guard was implemented or executed; G3 tests are specifications. The balance heuristic and the client-version check are unmeasured for local anvil. Whether the compiled allow-list can be overridden by a developer rebuilding the code is by design (a code change is an explicit decision), not a runtime configuration route.

## Status
Evaluation: complete. Implementation: **not started** (Phase 2, requires approval).
