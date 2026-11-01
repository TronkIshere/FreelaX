# P2-M11 validation report: frontend flows and simulation labelling

Date: 2026-11-02. No Fuji request or signer, no Mainnet contact, no real funds. Solana UI code is untouched except one additive, flag-guarded mount line in `ContractLifecycle.tsx`.

## 1. Delivered
| File | Purpose |
|---|---|
| `AvalancheEscrowPanel.tsx` | per-contract panel: environment banner, enable (client only), wallet-signed fund / approve extension / release, simulation-only "simulated account" actions, transaction list with state, badge, explorer link, auto-refresh while unfinished, error display |
| `avalanchePresentation.ts` | state text for all ten states, environment badge/banner, explorer URL builder (http(s) only, valid 32-byte hash only), action names |
| `ContractLifecycle.tsx` | mounts the panel only when `VITE_AVALANCHE_ENABLED=true` and the viewer is a contract party; default build shows nothing new |
| `styles.css` | additive banner and badge rules |

## 2. Requirement mapping
| Requirement | Status |
|---|---|
| Environment banner; "Mô phỏng" / "Giao dịch testnet (Fuji)" badges per environment | PASS (tested) |
| Ten transaction states shown accurately; CONFIRMED explicitly "no business change yet", UNKNOWN "do not resend", FINALIZED the only state described as applying results | PASS (tested) |
| No misleading real-money wording; no Mainnet wording | PASS (tested by text assertion; Vietnamese copy not reviewed by a native editor) |
| Explorer link per record, only from backend-supplied base URL and valid hashes | PASS (tested; Fuji base URL comes from the gateway config) |
| UI cannot bypass backend authorization: it sends only an idempotency key; calldata and typed data come from the backend; the wallet adapter validates chain; nothing is amount- or party-configurable | PASS |
| Wallet refusal, wrong network and Mainnet wallet surface an error and record nothing | PASS (adapter tests M10, panel test) |
| Simulated-account actions only when the gateway reports local simulation and backend signers | PASS (tested; hidden on Fuji) |
| Client-only enable; freelancer sees an informative message | PASS (tested) |
| Flows for submit, revise, dispute, resolve, extension request and mutual refund in the UI | **NOT IMPLEMENTED** in the panel: they need ids or signatures from existing submission/dispute screens (the API methods exist); only fund, extension approval and release are driven from the panel |
| Display of amounts/deadlines from chain state, legacy Solana contract labelling, admin resolve screen | **NOT IMPLEMENTED** |
| Real-browser check with a wallet extension | **NOT PERFORMED** |

## 3. Results
| Check | Result |
|---|---|
| `tsc -b`, `vite build` | clean / built |
| New tests | `AvalanchePanel.test.tsx` 10 pass, `evmWallet.test.ts` 24 pass |
| Full Vitest | 345 pass in 15 files; the same 13 files as in M10 cannot load because Node 22.10 cannot `require()` ESM `uuid` (pre-existing, environmental). `ContractLifecycle.test.tsx` is among them, so the mount line is verified only by typecheck/build |
