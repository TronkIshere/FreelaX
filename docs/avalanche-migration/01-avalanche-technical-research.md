# 01. Avalanche technical research

Research date: **2026-11-01**. Tags: **V** verified (page read or live RPC query this session), **S** search snippet / secondary, **U** unverified or prior knowledge. Everything tagged S or U, and every address, must be re-verified in Phase 1 before use in configuration. Sources are official unless stated.

## 1. Architecture
- Primary Network = P-Chain, X-Chain, C-Chain; C-Chain is an EVM chain with Geth-compatible JSON-RPC (V, https://build.avax.network/docs/rpcs/c-chain). All Primary Network chains use Snowman++ (ProposerVM, stake-weighted proposer slots) (V, docs.avax.network / build.avax.network).
- Before Helicon the C-Chain ran Coreth; since Helicon it runs the Continuous Execution VM (avalanchego `vms/saevm/cchain`). AvalancheGo v1.15.2 (2026-10-22) removed Coreth; the public mainnet node queried live reported avalanchego/1.15.1 (V, github.com/ava-labs/avalanchego releases, live RPC).
- Fees: ACP-176 dynamic gas target/price (Activated), ACP-226 dynamic block delay with `timestampMilliseconds` (Activated, seen in live blocks). ACP-283 (dynamic minimum gas price) is listed as part of Helicon but the ACP repo README still says Proposed: **unresolved discrepancy**. Read price via `eth_gasPrice`/`eth_feeHistory`; never hard-code (V/S).
- Avalanche L1s are separate chains with own validators, fee token and bridging. **Recommendation: C-Chain only.** Reason (inference, U): USDC is native on C-Chain; an L1 adds validator, fee-token and bridge overhead with no FreelaX requirement it would satisfy. Revisit only on a measured requirement (e.g. permissioned compliance, throughput).

## 2. Helicon and Continuous Execution (ACP-194)
- Status: Fuji activated 2026-07-28 15:00 UTC; Mainnet activated 2026-09-22 15:00 UTC (S: Avalanche Academy Helicon page, Chainstack; one snippet still reads "Scheduled" - re-check). Source ACP: ACP-194 "Continuous Execution" (Status: Implementable) in github.com/avalanche-foundation/ACPs.
- Lifecycle: **Proposed -> Accepted -> (variable delay) Executed -> (tau = 5s) Settled** (S/V docs).
  - *Accepted*: consensus fixed the order; the block was checked only that the sender can pay worst-case fees. It does **not** guarantee success: a tx can still revert or run out of gas. Acceptance guarantees eventual execution.
  - *Executed*: the async executor ran it from a FIFO queue.
  - *Settled*: a later accepted block records the state root and receipts root.
- Block tags (V, C-Chain RPC docs): `latest` = last executed block; `pending` = last executed by default (if `api-resolve-pending-to-last-executed` is false it is last accepted and state reads reject it); `safe` and `finalized` = last settled block.
- `eth_getTransactionReceipt` and state reads wait for the accepted block to execute. **`eth_getLogs` and log filters do not wait**: for an unexecuted block they return no logs (or an error when no address/topic filter). Near head, "no logs" does not mean "no events".
- Header changes: `gasUsed` is the sum of tx gas limits; `baseFeePerGas` is an upper bound; receipts hold true `gasUsed`/`effectiveGasPrice`. Gas charged is max(gasUsed, gasLimit/2), so inflated gas limits cost more.
- Subscriptions: `eth_subscribe` supports `logs`, `newHeads`, `newPendingTransactions`; Avalanche adds `newAcceptedTransactions` (WebSocket only, `fullTx` flag, fires at acceptance before execution). **UNVERIFIED**: whether `newHeads` fires at acceptance or execution (test on Fuji).
- Removed RPCs: `admin`, `warp`, `personal` namespaces, `eth_accounts`, `eth_coinbase`, `eth_etherbase`, 8 `debug_*` methods; `avax.getAtomicTxStatus` deprecated. `eth_call`, `eth_getLogs`, `eth_sendRawTransaction`, `eth_getBlockByNumber` unaffected.

## 3. Recommended FreelaX transaction lifecycle
Distinguish eight stages: created, signed, submitted, accepted, executed, receipt available, logs available, business finalized.

| Observation | Meaning | Internal handling |
|---|---|---|
| tx hash computed and persisted before send | created/signed | step `PENDING`, persist tx hash, nonce, signer, chainId |
| `eth_sendRawTransaction` returned hash | submitted | `PENDING`; submission is not proof |
| `eth_getTransactionByHash` found, no receipt | accepted or mempool, not executed | `PROCESSING`; re-query only |
| receipt status 0x1 | executed successfully | `PROCESSING` until depth/`finalized` policy met |
| receipt status 0x0 | executed and reverted (definitive) | `FAILED` with revert reason if decodable |
| receipt + logs decoded + (`finalized` or N blocks) + contract state read matches | business finalized | `CONFIRMED`, then call existing `confirmChainSettlement` |
| not found after window | unknown | `UNKNOWN`; check `eth_getTransactionCount` (latest/finalized) vs persisted nonce: nonce consumed by a different hash = replaced (definitive failure of this attempt), otherwise re-broadcast the same raw tx |

Design rules:
- **Keep UNKNOWN, re-query, idempotency, reconciliation and duplicate-submission protection.** Fast finality does not remove them; only the Solana-specific reasoning (blockhash expiry makes "not found" safe) is replaced by nonce and tx-hash reasoning.
- Use receipt (status 1) as the minimum execution proof; add `finalized` tag or N confirmations for release of business effects. Accept only logs from the configured contract address and decode by `(txHash, logIndex)`.
- Never treat empty `eth_getLogs` near head as absence; confirm with receipt or a state read (`eth_call` on escrow).
- **Internal states:** do not change persisted enums. Existing step statuses (`PENDING`, `PROCESSING`, `UNKNOWN`, `CONFIRMED`, `FAILED`) cover the table. Add *columns* (tx hash, chain id, nonce, accepted/executed block, first-seen time) via a forward-only migration in Phase 4 instead of new status values. Separate ACCEPTED/EXECUTED enums are not needed in the ledger; they are derivable observations in the Avalanche module.
- Open: a tx accepted but reverting at execution is only known after execution; the module must poll receipts, not trust acceptance (U/inference; see [07](07-open-questions.md)).

## 4. Networks (V unless noted)
| | Mainnet C-Chain | Fuji C-Chain |
|---|---|---|
| Chain ID | 43114 (0xA86A) (live) | 43113 (0xA869) (live) |
| HTTP RPC | https://api.avax.network/ext/bc/C/rpc | https://api.avax-test.network/ext/bc/C/rpc |
| WebSocket | wss://api.avax.network/ext/bc/C/ws | wss://api.avax-test.network/ext/bc/C/ws (S, docs example) |
| Explorer | https://explorer.avax.network/c-chain | https://explorer-test.avax.network/c-chain |
Snowtrace (snowtrace.io, testnet.snowtrace.io) is linked from Circle/Avalanche docs; Routescan and current Snowtrace status are U. Public API does not serve `eth_getChainConfig`. Public-endpoint rate limits and `eth_getLogs` range are **not documented on the checked page (U)**. Node holds **no accounts**: `eth_sign`/`eth_sendTransaction` always fail; sign locally and use `eth_sendRawTransaction` (V, C-Chain RPC docs). Avalanche-only: `eth_baseFee`, `eth_suggestPriceOptions` (V).

Fuji workflow: AVAX from the Avalanche Console "Get Test AVAX" faucet (amounts/limits U); USDC from https://faucet.circle.com (Avalanche Fuji listed; 20 USDC per request, one request per asset/network per 2h per address, S); deploy with Foundry or Hardhat against 43113; verification through explorer/Snowtrace API (flow U, check current docs). Mainnet and Fuji values must live in separate configuration profiles; never reuse a Fuji address on Mainnet.

## 5. USDC and ERC-20
- Native USDC mainnet: `0xB97EF9Ef8734C71904D8002F8b6Bc66Dd9c48a6E` (Circle address page https://developers.circle.com/stablecoins/usdc-contract-addresses, S/V; undated page). Fuji USDC: `0x5425890298aed601595a70AB815c96711a31Bc65` (same page; Circle says no financial value).
- Live probes of both: name "USDC Coin", symbol "USDC", decimals 6, version "2", not paused; both expose `DOMAIN_SEPARATOR`, `nonces`, `authorizationState`, and `PERMIT_TYPEHASH` equals the standard EIP-2612 typehash. So EIP-2612 `permit` and EIP-3009 `transferWithAuthorization` interfaces are **present** on both (V live). Exact FiatToken version behind each proxy is **unverified**; "same ABI as production" is supported by probes, not proven. Both are upgradeable proxies (implementations and admins differ) with blacklist/pauser roles (Circle `stablecoin-evm` README).
- Bridged USDC.e: `0xA7D7079b0FEaD91F3e65f86E8915Cb59c1a4C664` (recalled; **not verified against an official page**). On-chain it reports symbol "USDC.e", decimals 6, and no `DOMAIN_SEPARATOR` (no permit). Its name is also "USDC Coin", so **never distinguish by name or symbol alone**.
- Asset identification strategy: explicit supported-asset config keyed by `(chainId, address)`; at startup assert `decimals()==6`, `symbol()`, EIP-712 version, and that the code at the address is a known proxy implementation if policy requires; reject any ERC-20 not in the list; the escrow contract holds an immutable (or admin-set-once) allowed token, and the backend compares `Transfer` logs against it. Different config per environment.
- Standards and relevance: ERC-20 (token calls: `balanceOf`, `approve`, `transferFrom`, `Transfer`); EIP-155 (chain-id replay protection; always sign with the configured chain id); EIP-712 (typed signed data: backend-signed authorizations, arbiter resolutions); EIP-2612 (permit; present on USDC per probe; optional later optimisation); EIP-3009 (authorization-based transfer, an alternative to approve); EIP-1193 (browser wallet provider API). Sources: https://eips.ethereum.org/EIPS/eip-20, -155, -712, -2612, -3009, -1193 (not fetched, U).
- Risks: approval race (use exact amounts or `increaseAllowance`-style), blacklist/pause/upgrade of USDC can freeze escrowed funds (design recovery: pull payouts, admin reroute of blacklisted payee), fake tokens with symbol "USDC".
- Recommendation: MVP uses ordinary `approve` + escrow `fund` (two txs, wallet-agnostic); evaluate permit/EIP-3009 only after verifying the deployed token source in Phase 1.

## 6. Solidity and tooling
- Solidity 0.8.38 (2026-10-22, V). OpenZeppelin Contracts v5.7.0 (2026-07-29, V; docs example `pragma ^0.8.24`). Foundry v1.8.7 (2026-10-29, V). Hardhat npm latest 3.21.2 (V). Avalanche docs state C-Chain supports **Cancun, not Pectra**, and Solidity 0.8.30+ defaults to Pectra, so set `evm_version = "cancun"` explicitly (S; could not confirm current in Nov 2026; verify, especially after Helicon).
- **Recommendation: Foundry** (native Solidity tests, fuzz and invariant testing, anvil forking of Fuji) plus Slither in CI. Hardhat 3 would add a Node TS test toolchain not otherwise needed. Frontend already uses Node, but contract tests do not need it.
- Escrow patterns (reasoning, sources not fetched): one shared escrow contract with a per-job struct keyed by `bytes16/bytes32 milestoneId` is the cheapest and smallest audit surface; factory + EIP-1167 clones only if per-job fund isolation is legally/operationally required; one contract per job costs a deployment per job. **Immutable over upgradeable** unless governance (multisig + timelock) exists; USDC itself is upgradeable so trust assumptions already exist. Use `ReentrancyGuard`, `SafeERC20`, `Pausable`, `AccessControl`, checks-effects-interactions, pull-over-push payouts, `EIP712`/`ECDSA` for signed arbiter decisions.
- On-chain vs MySQL authority: on-chain = custody, status, deadlines, extension/revision counters, arbiter resolution (mirrors today's Anchor rules); MySQL = business/ledger/fees/tax/notifications/disputes narrative. Do **not** port `PaymentFlow` into Solidity. Fees: today not on-chain; keep off-chain unless business decides otherwise.

## 7. Wallets and signing
- Users: MetaMask, Core Wallet (status of `wallet_addEthereumChain` details U), WalletConnect/Reown AppKit (package names vary across docs, check at install), via EIP-1193. Frontend libs: viem 2.57.14, wagmi 3.7.9, ethers 6.17.0 (V npm). Use `wallet_switchEthereumChain`/`wallet_addEthereumChain` to enforce chain 43114/43113; detect wrong chain from `eth_chainId` before enabling funding buttons.
- Users sign: `approve`, `fund`, mutual-refund approvals, dispute opening (their own txs, they pay AVAX gas unless a relayer is added later). Backend signs: arbiter resolutions and operator actions with a **KMS-held key** (AWS KMS `ECC_SECG_P256K1` supports SIGN_VERIFY and is non-exportable (S); returns DER, so extract r,s, normalise low-s, recover v by testing both; no ready Java recipe found; Vault secp256k1 support unchecked). Never send private keys to the frontend or store plaintext keys. The existing Solana local signer should not be copied; keep a localnet-style dev signer only for Fuji/anvil with the same guards.
- Differences from Solana: no SPL token accounts, approve + `transferFrom` replaces ATAs; gas in AVAX; login signatures use EIP-191/712 or SIWE instead of Ed25519; no co-signed partially signed transactions, so backend co-signing is replaced by contract roles or EIP-712 authorizations (open decision).
- Nonce management: DB-backed per-signer nonce allocation (`FastRawTransactionManager` keeps nonce in memory, unsafe across restarts/instances (U)). Replacement: same nonce, higher fees (~10%+ typical, U).

## 8. Java integration
- web3j: Maven Central latest 6.1.0 (lastUpdated 2026-10-23; GitHub release dated 2026-10-27, discrepancy unexplained) (V). Actively maintained. Java 21 / Spring Boot 3.5 compatibility **not verified** (needs a dependency-tree check: OkHttp, Jackson, Kotlin).
- **Recommendation:** use web3j as ABI encode/decode + signing + JSON-RPC layer (`FunctionEncoder`, `EventEncoder`, `Credentials`/custom signer, `Web3j`), wrapped by a FreelaX-owned module with a DB-backed nonce allocator, gas strategy (EIP-1559 via `eth_feeHistory`/`eth_baseFee`) and receipt tracking. Generated wrappers are optional. Raw JSON-RPC offers no clear advantage; other libraries not materially better (U).
- Integration with Spring: submission is asynchronous (persist hash, return); receipt checking runs inside the **existing** reconciliation/timeout schedulers, not a new blocking loop or second poller. Never rely on the node to hold keys.

## 9. RPC and infrastructure
- Development: Fuji public RPC + anvil fork. Production: paid dedicated provider as primary, second provider as fallback, public endpoint only as last resort. Avalanche docs list community providers and commercial ones (PublicNode, All That Node, Infura, dRPC, BlastAPI, Ankr, Chainstack; inconsistent across pages; Alchemy supports Avalanche, S). Self-hosting AvalancheGo requirements unchecked (U).
- `eth_getLogs` limits observed only from providers (S): QuickNode 10,000 blocks, Chainstack ~100,000 recommended, Dwellir 1,000-5,000; typical error -32005. Public-endpoint limit unknown (U).
- Event indexing: for FreelaX scale, **direct log scanning is sufficient**: store `last_scanned_block`, scan chunks of at most 2,000 blocks, wait N confirmations or `finalized`, dedupe `(txHash, logIndex)`, reconcile against `eth_call` state reads. WebSocket not required. Data API (AvaCloud) exists (free tier 8,000 CU/min, S) but is not needed initially. No new database or indexing service is justified in Phase 0.
- Guard against malicious/misbehaving RPC: verify `eth_chainId` at startup and on each client, cross-check head between two providers, validate receipts' `to`/contract address and logs' emitter.

## 10. Source list
build.avax.network/docs/rpcs/c-chain; docs.avax.network; github.com/ava-labs/avalanchego releases; github.com/avalanche-foundation/ACPs (ACP-176, -194, -226, -283); Avalanche Academy Helicon page; developers.circle.com/stablecoins/usdc-contract-addresses; faucet.circle.com; github.com/circlefin/stablecoin-evm; docs.web3j.io and Maven Central (web3j); soliditylang.org; docs.openzeppelin.com/contracts; book.getfoundry.sh; hardhat.org; eips.ethereum.org; developers.avacloud.io. Where sources disagreed (Helicon "Scheduled" vs active; ACP-283 status; web3j release dates) the disagreement is noted above.
