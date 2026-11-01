# Phase 1 - Solidity, Foundry and OpenZeppelin evaluation

Project: `prototypes/solidity-foundry/` (`foundry.toml`, `src/Phase1Lab.sol`, `test/Phase1Lab.t.sol`, `test/UsdcFork.t.sol`). Evidence: [forge-unit-tests.txt](evidence/forge-unit-tests.txt), [forge-usdc-fork-tests.txt](evidence/forge-usdc-fork-tests.txt), [slither-phase1lab.txt](evidence/slither-phase1lab.txt), [evm-fork-probe-official.json](evidence/evm-fork-probe-official.json), [java-local-experiments.json](evidence/java-local-experiments.json) (J2).

## Versions (OBS)
Foundry 1.8.7 (Docker, pinned), solc 0.8.38, OpenZeppelin Contracts 5.7.0, forge-std (installed via `forge install`), Slither 0.11.6. All three Phase 0 version claims (Solidity 0.8.38, OZ 5.7.0, Foundry 1.8.7) are **confirmed to exist and work**.

## S1. Lab contract (not the escrow)
`Phase1Lab` (OZ `AccessControl`, `EIP712`, `ECDSA`, `SafeERC20`): role-gated setter, ERC-20 `pull`, event emission, custom-error revert, two mutually exclusive timestamp checks (`actUntil`: allowed while `ts <= deadline`; `actAfter`: allowed once `ts > deadline`), EIP-712 authorization with per-signer nonce and deadline. It has **no FreelaX business logic** and was **never deployed to Fuji or Mainnet** (no funds; and Fuji deployment was unnecessary because every question was answerable locally or on a fork).

## S2. Results
| Check | Result |
|---|---|
| `forge build` with explicit `evm_version = "cancun"`, optimizer 200 runs | PASS (solc 0.8.38 compiles in about 1.4-2.0 s) |
| Unit tests (6) | **PASS**: role revert, event emission, custom-error revert, deadline boundaries, EIP-712 positive/replay/expiry/wrong account/tamper/wrong chain/wrong contract, garbage signature |
| Fuji-fork tests (7) | **PASS** ([native-usdc-validation.md](native-usdc-validation.md)) |
| Local EVM deployment | PASS via Java/web3j on anvil: deployment gas **983,088**, runtime code **4,136 B** (initcode 4,900 B) |
| ABI/bytecode generation | PASS: `forge inspect Phase1Lab bytecode` fed the Java deployment directly |
| Gas estimates (local, Cancun, optimizer 200) | `setValue` 47,735 (first write); `actUntil` ~21.6-23.2k; `useAuthorization` median 30,708, avg 32,416 (fuzz-free single runs; **not** Avalanche-specific or comparative claims) |
| Event decoding | PASS (Java) |
| Slither 0.11.6 | Runs (via container); **3 results, all `block.timestamp` comparison informational** on the intentional deadline checks. No other detector fired on the lab contract. |
| Forge lint | informational warnings only (`environment-read-across-mutation` in tests) |

## S3. Which EVM fork does Fuji run? (resolves a Phase 0 open question)
Read-only `eth_call` with a state-override stub against the official Fuji RPC ([evm-fork-probe-official.json](evidence/evm-fork-probe-official.json)):
| Probe | Result |
|---|---|
| PUSH0 (Shanghai) | executes |
| TSTORE/TLOAD (EIP-1153, Cancun) | executes (returns stored 1) |
| MCOPY (EIP-5656, Cancun) | executes (copy verified) |
| BLOBBASEFEE / BLOBHASH (Cancun) | execute |
| CLZ 0x1e (Osaka) | **invalid opcode** |
| EIP-2537 BLS precompile `0x0b` (Pectra) | **absent** (empty result) |
| EIP-2935 history contract (Pectra) | **absent** |
| EIP-4788 beacon-root contract (Cancun) | absent (Avalanche has no beacon chain; header still has `parentBeaconBlockRoot`) |
**Conclusion (OBS, Fuji only):** Cancun opcodes work; the Pectra markers tested are absent; Osaka is not live. This supports the Avalanche-docs claim reported in Phase 0 and the choice `evm_version = "cancun"` (solc 0.8.30+ defaults to Pectra, so the setting **must** stay explicit). EIP-7702 was not tested; Mainnet was **not** probed for this (UNVERIFIED for Mainnet, though Helicon is reported active there).

## S4. Foundry vs Hardhat
Only Foundry was *run*. Hardhat was only version-queried (`npm view`: hardhat 3.21.2, `@nomicfoundation/hardhat-foundry` 3.0.5) and **not installed**, so the Hardhat column is reasoning, not measurement.
| Criterion | Foundry (measured) | Hardhat (not run) |
|---|---|---|
| Maturity | Works with the newest solc/OZ here; needed one test refactor for stack-too-deep, no tool defects | version exists (3.21.2); not evaluated |
| Solidity-native tests, fuzz, invariants, cheatcodes (`vm.sign`, `vm.chainId`, `vm.warp`, fork) | **Used extensively**; EIP-712 negative cases and Fuji-fork permit/EIP-3009 tests were short because of cheatcodes | would need TypeScript tests + viem/ethers |
| Forking a live chain | `--fork-url` Fuji worked (fork tests 5 s) | not tested |
| CI | Docker image or `foundryup`; pinned image used here | Node toolchain required |
| Java integration | Artifacts (`forge inspect`) consumed by web3j test unchanged | n/a (ABI is the same standard) |
| Windows developer workflow | No native install was done; Docker wrapper script works but is slower to type; Linux CI unaffected | Node native on Windows |
| Maintenance cost | One extra toolchain (Rust binaries) | One extra toolchain (Node) already present for frontend |
**Recommendation (evidence-based on Foundry's side only): Foundry** for contracts, tests, fuzz/invariants and fork tests, plus Slither in CI. Hardhat adds nothing needed by the backend; revisit only if the team prefers TypeScript tests. The decision is reversible because contracts are plain Solidity.

## S5. Gaps
- Web3j wrapper generation from ABI: not tested (the prototype hand-encodes with `FunctionEncoder`); **UNVERIFIED**.
- Invariant/fuzz testing: not exercised (no escrow exists); Phase 3.
- Explorer verification (Snowtrace/Routescan flow) and Fuji deployment: not performed (no funds): **INCONCLUSIVE**.
- Slither on a real escrow: Phase 3.

## Status
Foundry + solc 0.8.38 + OZ 5.7.0 viable, reproducible: **PASS**. Fuji deployment and explorer verification: **NOT PERFORMED (blocked: no funds)**.
