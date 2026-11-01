# P2-M10 validation report: frontend neutral wallet interface and EVM adapter

Date: 2026-11-02. No Fuji request, signer or transaction; no Mainnet contact; no real funds. The Solana wallet module `escrowWallet.ts` and its tests are unchanged.

## 1. Delivered
| File | Purpose |
|---|---|
| `frontend/src/evmWallet.ts` | `EscrowWalletPort` (neutral `connect`), `EvmWallet` adapter over an injected EIP-1193 provider (`window.ethereum`): connect, chain check/switch, `sendPrepared` (eth_sendTransaction of backend-prepared calldata only), `signTypedData` (eth_signTypedData_v4 of backend-prepared EIP-712 data only), typed `WalletError` codes |
| `frontend/src/avalancheTypes.ts` | API types including the ten transaction states |
| `frontend/src/api.ts` | six additive methods for `/contracts/{id}/avalanche` (status, enable, prepare, execute, record, refresh) |
| `frontend/src/evmWallet.test.ts` | 24 Vitest tests |

## 2. Safety properties (tested)
- Allow-list of exactly chains 31337 (local) and 43113 (Fuji); 43114, 1, 0, negative, fractional and NaN are refused **before the wallet is contacted**; a wallet found on Mainnet is refused and never asked to send or sign.
- A prepared transaction or typed-data domain must name the active environment chain, otherwise `CHAIN_MISMATCH`; the live wallet chain is re-read before sending; calldata, addresses, hashes and signature shapes are validated; the typed-data domain must carry a valid verifying contract.
- The adapter only calls `eth_requestAccounts`, `eth_chainId`, `wallet_switchEthereumChain` (allow-listed ids only), `eth_sendTransaction`, `eth_signTypedData_v4`. It never holds, derives, exports or asks for a private key, mnemonic or raw signature, and has no RPC URL or backend-signer path. Amounts, parties and terms come only from backend-prepared actions.
- Wallet declines map to `USER_REJECTED`; raw provider errors are not shown.
- No new dependency: viem was **not** added (a thin dependency-free EIP-1193 adapter covers the required calls and keeps the bundle and audit surface unchanged). WalletConnect not added (O-12).

## 3. Results
| Check | Result |
|---|---|
| `tsc -b` | clean |
| `evmWallet.test.ts` | 24 / 24 pass |
| Full Vitest run | 335 tests pass in 14 files; **13 files cannot load (pre-existing, environmental)**: Node v22.10.0 cannot `require()` the ESM-only `uuid` pulled by `@solana/web3.js` → `rpc-websockets`. Reproduced identically with all M10 changes stashed, so it is not caused by this milestone, but those 13 suites (including all Solana wallet and page suites) are **not currently verifiable** here. Node >= 22.12 is expected to fix it; needs a Node upgrade or a lockfile decision by the owner |

## 4. Not done in M10
Real-browser wallet check (no wallet extension available here); UI wiring is M11.
