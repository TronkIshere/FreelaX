# FreelaX project scope: Avalanche-powered development simulation

Status: **permanent project-wide constraint**, recorded 2026-11-01 on the project owner's direction. It changes only by an explicit decision of the project owner. It applies to every phase, document and future change. Historical documents are preserved and are read **through** this constraint (see section 9).

## 1. Canonical definition
> FreelaX is an Avalanche-powered development simulation of a USDC-based freelance payment and escrow platform. It demonstrates blockchain-based payment workflows, milestone escrow, transaction verification, and AI-assisted financial monitoring without handling real-world funds or providing actual financial settlement.

FreelaX uses **real blockchain technology** (a real EVM, real signatures, real transactions on a public testnet) for development and demonstration. **All financial operations are simulated.**

Tiếng Việt (tóm tắt): FreelaX là bản mô phỏng phát triển chạy trên Avalanche. Hệ thống dùng công nghệ blockchain thật cho mục đích phát triển và trình diễn, nhưng mọi nghiệp vụ tài chính đều là mô phỏng; không xử lý tiền thật và không quyết toán tài chính thật.

## 2. What FreelaX simulates
Freelancer deposits and client payments; escrow funding; milestone approval; USDC transfers; freelancer payouts; refunds; dispute settlement; platform fees; tax calculations; financial reporting; AI-assisted monitoring of those records.
These are **simulated business operations**. They are carried out with locally simulated tokens or Fuji testnet tokens. They are demonstrations of how such workflows *could* operate.

## 3. Why Avalanche
Decision D1 ([README](avalanche-migration/README.md)): an engineering and product-architecture choice: EVM/Solidity ecosystem and tooling (Foundry, OpenZeppelin, Slither), a Circle-issued USDC contract with a public testnet deployment, Java access through EVM libraries, standard wallets, and long-term maintainability. **No speed or cost advantage over Solana is claimed**, and no partnership or grant is recorded. Phase 1 confirmed the stack works for the simulation (see [phase-1](avalanche-migration/phase-1/README.md)).

## 4. Supported environments (the only ones)
| Environment | Network | Assets | Transactions | Allowed use |
|---|---|---|---|---|
| **LOCAL_SIMULATION** | Anvil or another isolated local EVM (chain id 31337 for a plain local chain) | locally deployed **simulated USDC** with no value | local only; deterministic, repeatable | unit/integration tests, repeatable scenarios, fault injection |
| **AVALANCHE_FUJI** | Avalanche Fuji C-Chain, chain id **43113** | **testnet AVAX and Circle testnet USDC** (no intended monetary value) | **real transactions on the public testnet** | integration experiments and demonstrations |
| **MAINNET** | Avalanche C-Chain chain id 43114 | n/a | **none** | **Out of scope. Not implemented, not configured, not deployable.** |

Never connect LOCAL_SIMULATION to Mainnet. Never use real funds or production credentials in any environment.

## 5. Why no real money is involved
- Local tokens are created by us and have no value. Fuji AVAX and USDC come from faucets and carry no intended real-world value (Circle's page states Fuji USDC has no financial value).
- No bank account, payment provider, fiat conversion, cash withdrawal or production financial credential is connected. Fiat legs (USD in, VND out, tax certificates) remain mocks, clearly labelled.
- A simulated balance is a database/ledger figure for demonstration. It is not a claim on funds anywhere.

## 6. Local simulation versus public testnet
| | LOCAL_SIMULATION | AVALANCHE_FUJI |
|---|---|---|
| Who sees the transaction | nobody but the developer's machine | anyone (public testnet, explorer) |
| Finality/latency | instant, deterministic | real consensus: measured about 5 to 9 s to `finalized` (Phase 1) |
| Chain id | 31337 (a **local fork of Fuji reports 43113**, Phase 1 J14, so chain id alone does not identify the environment) | 43113 |
| Tokens | simulated USDC | Circle testnet USDC |
| Can demonstrate Helicon accepted vs executed behaviour | **No** (Phase 1) | Yes (observed read-only; not yet with our own transactions) |
| Records must be labelled | "Local simulation" | "Testnet transaction (Fuji)" |
A Fuji transaction is a genuine public-testnet transaction; it is still **not** a financial settlement. A local transaction exists only in the local simulation.

## 7. Terminology (user interface and documents)
Prefer: Simulation Balance, Testnet USDC, Simulated Escrow, Testnet Transaction, Development Environment, Simulated Payout, Demonstration Only.
Avoid: real wallet balance, withdrawable balance, real payment received, cash withdrawal, actual financial settlement.
A Fuji transaction must be shown as a **testnet** transaction; a local one as **local simulation**. Simulated financial records (ledger, fee, tax, payout figures) must be visibly distinguished from on-chain transaction records.

## 8. Safety and isolation requirements for implementation
Detailed evaluation: [phase-1/simulation-environment-guards.md](avalanche-migration/phase-1/simulation-environment-guards.md). Summary of the binding requirements:
1. Only LOCAL_SIMULATION and AVALANCHE_FUJI may be configured. **Mainnet chain id 43114 is rejected**, as is every chain id not on the allow-list; the allow-list is **not extensible through ordinary environment configuration**.
2. No Mainnet fallback, no silent network fallback, no implicit switch between local and Fuji.
3. The environment is explicit configuration; every persisted chain record carries its environment and chain id.
4. No private keys or production secrets in Git; no production wallet imports.
5. The application fails closed (refuses to start) on an unsupported network, a Mainnet endpoint or a production-looking credential.

## 9. Interaction with existing documents (historical content is preserved)
| Existing statement | Treatment under this scope |
|---|---|
| Phase 0 roadmap Final Phase "Mainnet deploy, switch primary chain" and backlog 36 | **Out of scope / withdrawn**; "primary chain" now means the primary *development* chain (Avalanche), with Solana kept as the legacy reference |
| Phase 6 "production readiness", external audit, key ceremony, Mainnet multisig | reinterpreted as **quality gates for the simulation**; audit and key-ceremony items need an owner decision on whether they are still wanted |
| "Startup guard rejects dev signer on the Mainnet chain id" | superseded by "reject the Mainnet chain id entirely" |
| Root README "Những gì sẽ bổ sung": real money, real partners, real wallets, real tax | **out of scope** under this constraint; kept as historical wish-list |
| Phase 0 decisions D1 to D4 | **unchanged**; no genuine conflict found (see the report in the task) |
| Phase 1 read-only Mainnet probes (USDC identity, no writes) | historical; **no further Mainnet contact, including Mainnet fork tests, is planned** |
Technical findings (RPC limits, transaction lifecycle, EIP-712, Java compatibility) remain valid for Fuji and local use.
