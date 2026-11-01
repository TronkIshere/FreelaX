# Phase 1 - Environment setup and exact tool versions

> **Scope amendment (2026-11-01):** FreelaX is a development simulation with no real funds ([SIMULATION_SCOPE](../../SIMULATION_SCOPE.md)); Mainnet is out of scope. Mainnet probes in this phase were read-only and historical; no further Mainnet contact (including a Mainnet fork test) is planned. Guard evaluation: [simulation-environment-guards.md](simulation-environment-guards.md).

Run date: **2026-11-01** (host clock UTC+7). Host: Windows 11 Home 10.0.26300, Git Bash/PowerShell. Everything below was observed on this machine; nothing was assumed.

## Tool inventory (observed)
| Tool | Result | Notes |
|---|---|---|
| Java | Oracle JDK **21.0.2** (`java -version`); JDKs present: 20, 21, 17 (Corretto), 1.8 | **`JAVA_HOME` points to JDK 20**, so `mvn` fails with `release version 21 not supported`. Prototype runs set `JAVA_HOME` to JDK 21 for the process only; the machine setting was not changed. |
| Maven | **3.9.16** from `~/.m2/wrapper/dists` (3.9.11, 3.9.12, 3.9.16 present) | `mvn` is not on `PATH`; no `mvnw` in the repo. |
| Existing project Java level | `pom.xml` `<java.version>17</java.version>`, Spring Boot parent **3.5.16** | **Discrepancy with the Phase 1 directive (Java 21).** Not changed. The prototype uses 21; Spring Boot 3.5.16 supports 17 and 21, so Phase 2 must decide whether the new module needs 21 (nothing in web3j 6.1.0 was found to require it, see [java-web3j-evaluation.md](java-web3j-evaluation.md)). |
| Node / npm | 22.10.0 / 10.9.0 | Used for WebSocket/timestamp probes (global `WebSocket`/`fetch`). |
| Python | 3.13.3 | Used for the stdlib RPC probe. Its TLS store rejected the Fuji certificate ("certificate has expired") while curl and Node accepted it; verification was **not** disabled, the probe shells out to curl instead. |
| Rust / Cargo | **not installed** | Not needed. |
| Docker | 29.3.1 (daemon running) | Used for Foundry, anvil and Slither so nothing is installed globally. |
| Foundry | **1.8.7** (`forge`/`cast`/`anvil`, commit `985eeea`, built 2026-10-29) | Official image `ghcr.io/foundry-rs/foundry:v1.8.7` (pinned). Not installed on the host. No Windows installer was run (avoids global changes). |
| Solidity | **0.8.38** (downloaded by forge) | `evm_version = "cancun"` set explicitly. |
| OpenZeppelin Contracts | **5.7.0** (`forge install --no-git`, tag `v5.7.0`) | `lib/` is git-ignored; reinstall command in `scripts/run-all.sh`. |
| Slither | **0.11.6** (pip, python:3.12-slim container, `solc-select` 0.8.38) | Run on the lab contract only. |
| web3j | **6.1.0** (Maven Central `latest`/`release`, lastUpdated 2026-10-23) | |
| Spring Boot | **3.5.16** | surefire 3.5.6, compiler plugin 3.14.1. |
| Hardhat (npm, not installed) | 3.21.2 (`npm view`) | Only version-queried; not run. |
| Git | 2.50.1, branch `master`, Phase 0 commit `1662a80` present | Untracked before this work: `.idea/*`, `.serena/` (left untouched). |

## Environment variables
`env | grep -iE 'rpc|avax|avalanche|fuji'` returned nothing. No RPC URL, key or token is configured in this shell. No secret was read or printed.

## Layout (all new, all isolated)
```
docs/avalanche-migration/phase-1/
  *.md                              deliverables
  evidence/                         raw JSON/text outputs (no keys)
  scripts/                          fuji_probe.py, forge.sh, start-anvil.sh, eip712_interop.sh, run-all.sh
  prototypes/solidity-foundry/      Foundry project (lab contract, tests, Fuji-fork USDC tests)
  prototypes/java-web3j/            standalone Maven project (NOT a module of the FreelaX parent)
  prototypes/ws-observer/           Node probes: WebSocket observer, finality probe, timestamp probe
```
The Java prototype has `spring-boot-starter-parent:3.5.16` as its own parent and is not listed in the root `pom.xml`. Build output (`target/`) and Foundry `lib/ out/ cache/` are git-ignored by local `.gitignore` files inside the prototypes.

## Reproduction
`scripts/run-all.sh` re-runs every experiment (needs docker, node >= 22, JDK 21 + Maven; set `JAVA_HOME` and `MVN`). Local anvil: `scripts/start-anvil.sh` (`:8545` chain 31337, `:8546` fork of Fuji). Containers `freelax-anvil` and `freelax-anvil-fork` were created for the run; they are disposable (`docker rm -f`).

## What could not be set up
| Item | Why | Impact |
|---|---|---|
| Funded Fuji wallet (AVAX + USDC) | Avalanche's "Get Test AVAX" faucet is a console web flow ([quick-start page](https://build.avax.network/docs/quick-start/networks/fuji-testnet) gives no limits or requirements); Circle's faucet is a web form. Both need interactive use; no account was created. | **No transaction was ever broadcast successfully to Fuji.** All on-chain write experiments ran on local anvil or on a local fork. See [transaction-lifecycle-experiments.md](transaction-lifecycle-experiments.md). |
| KMS account / RPC provider account | Not available, would be paid/credentialed | KMS signing not tested ([security-key-management-evaluation.md](security-key-management-evaluation.md)); provider limits only known for two free public endpoints. |
| Read-only SQL rail evidence (backlog 2) | Needs DB access and an operator | Not done. |
