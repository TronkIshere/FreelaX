# 04. Frontend integration plan (plan only; no frontend source is changed in this phase)

Current state (read-only inspection): React + TypeScript + Vite, Vitest (`npm test`), one chain dependency `@solana/web3.js`. `escrowWallet.ts` exposes `injectedSolanaWallet()`, `connectSolanaWallet()`, `signEscrowTransaction(wallet, base64Transaction)` and helpers that compare/check a partially signed Solana transaction; `window.phantom?.solana ?? window.solana` is the only wallet path. Callers (from the Phase 0 audit): `EscrowFunding.tsx`, `ContractLifecycle.tsx`, `Finance.tsx`, `MutualRefund.tsx`, `WalletLink.tsx`, `session.tsx`, `api.ts`, `types.ts`. Explorer links and chain labels appear in `Finance.tsx`/`financeStatus.ts`/`types.ts`.

## 1. Key difference to design for
Solana flow today: the backend returns a **partially signed transaction (base64)**, the wallet **signs and returns** it, the frontend submits. EVM flow: the backend returns a `PreparedTransaction` (`{to, data, value, chainId}` plus an EIP-712 authorization where required); the **wallet itself broadcasts** (`eth_sendTransaction`) and returns a **transaction hash**; the frontend posts the hash. Funding needs two wallet steps (`approve` exact amount, then `fund`). A neutral interface must therefore model "submit a prepared action and obtain a transaction reference", not "sign a base64 blob".

## 2. Neutral wallet interface (P2-M10)
Placeholders: `ChainWallet { chainFamily; connect(): Account; networkInfo(): {environment, chainId, label}; ensureNetwork(): void; submit(prepared: PreparedAction): TxRef; balances(assets): Balance[]; signMessage?(…) }`. Adapters: `solanaWallet` (wraps the existing functions **unchanged**) and `evmWallet` (EIP-1193 injected provider via viem; wagmi optional). `types.ts`/`api.ts` gain chain-neutral fields (`environment`, `isSimulated`, `explorerUrl`, `chainFamily`) additively. The existing Solana UI path keeps working and its tests stay green. Versions (viem, wagmi) are verified at install time; Phase 0's numbers are not assumed. WalletConnect/Reown is **not** in scope unless the owner asks (open decision O-12); injected wallets (MetaMask, Core) are the baseline.

## 3. EVM wallet behaviour (P2-M10)
- Connect through `eth_requestAccounts`; read `eth_chainId`; compare with the environment advertised by the backend config (never infer the environment from the chain id: a local fork reports 43113). On mismatch show a blocking "wrong network" state and offer `wallet_switchEthereumChain`/`wallet_addEthereumChain` **only** for the allow-listed chains (31337 for LOCAL_SIMULATION, 43113 for AVALANCHE_FUJI). **Chain id 43114 and any other chain is refused, with no "add network" offer.**
- Address validation (checksum) at link time; wallet link challenge uses an EIP-191 or EIP-712 signature (replacing the Ed25519 `signMessage` challenge); the backend verifies with the same typed data stack validated in Phase 1.
- Show exact token, amount, escrow contract and environment **before** each signature request; never request private keys or seed phrases.
- LOCAL_SIMULATION convenience (decision O-11): today the local demo uses backend-held auto wallets (`LocalAutoWalletService`). An EVM equivalent (backend-held ephemeral dev wallets) would be a **LOCAL_SIMULATION-only** feature behind the same guards (never enabled for AVALANCHE_FUJI users); the alternative is connecting an injected wallet to Anvil. Not decided.

## 4. Flows (P2-M11)
| Flow | Behaviour |
|---|---|
| Fund | request prepared action -> show terms -> `approve(exactAmount)` -> `fund` with the backend authorization -> post tx hash -> poll status until CONFIRMED/FINALIZED |
| Submit, revision, extension, dispute, release, mutual refund | the same pattern; release is also available to anyone after the deadline (UI explains) |
| Balances | testnet USDC balance read through the backend; local shows simulated USDC |
| Status | poll the neutral `TransactionStatus`; UNKNOWN shown as "checking", never as failure; REVERTED shown with a decoded reason where available |
| Errors | wrong network, user rejection, insufficient testnet AVAX (explain faucet), throttled provider (retry), boundary-deadline revert |

## 5. Terminology and labelling (binding, from the scope document)
Use: **Simulation Balance, Testnet USDC, Simulated Escrow, Testnet Transaction, Development Environment, Simulated Payout, Demonstration Only.** Avoid: real wallet balance, withdrawable balance, real payment received, cash withdrawal, actual financial settlement. Visual rules:
| Record | Label |
|---|---|
| Ledger figure (fee, tax, payout amounts, "simulation balance") | "Simulated" badge; never styled like a confirmed on-chain transfer |
| Local transaction | "Local simulation" badge, no explorer link |
| Fuji transaction | "Testnet transaction (Fuji)" badge with the testnet explorer link |
| Escrow record | "Simulated Escrow"; the underlying contract state is shown separately as a genuine testnet record when on Fuji |
A persistent environment banner ("Development Environment: LOCAL_SIMULATION" or "AVALANCHE_FUJI testnet, demonstration only") appears on every page that shows money-like figures. Existing wording that implies real money must be reviewed in P2-M11 (the Vietnamese strings for payout/withdrawal included), changing text only.

## 6. Milestones
| ID | Scope | Acceptance | Security | Tests | Rollback |
|---|---|---|---|---|---|
| P2-M10 | neutral `ChainWallet`, `evmWallet`, network handling, additive types | Solana wallet tests unchanged; EVM adapter unit tests with a mocked EIP-1193 provider; wrong-network and refused-chain tests (43114) | no key requests; allow-listed chains only; no hard-coded RPC secrets | Vitest unit and component tests | feature flag off; adapters additive |
| P2-M11 | flows, labelling, explorer URL per record, banner | Anvil end to end in the browser (injected wallet pointed at Anvil or the local dev-wallet option); Fuji flow after the funded wallet exists; terminology review passes | simulated figures visibly distinct from chain records | component tests, browser e2e on Anvil, Fuji manual run | flag off; text changes reversible |
The Solana UI and `@solana/web3.js` stay until legacy usage evidence exists ([10](10-legacy-solana-coexistence.md)).
