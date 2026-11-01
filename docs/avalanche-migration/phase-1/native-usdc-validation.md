# Phase 1 - Native USDC validation (Fuji, with Mainnet read-only comparison)

> **Scope amendment (2026-11-01):** FreelaX is a development simulation with no real funds ([SIMULATION_SCOPE](../../SIMULATION_SCOPE.md)); Mainnet is out of scope. Mainnet probes in this phase were read-only and historical; no further Mainnet contact (including a Mainnet fork test) is planned. Guard evaluation: [simulation-environment-guards.md](simulation-environment-guards.md).

Run: 2026-11-01. Evidence: [fuji-probe-official.json](evidence/fuji-probe-official.json), [mainnet-usdc-readonly.txt](evidence/mainnet-usdc-readonly.txt), [forge-usdc-fork-tests.txt](evidence/forge-usdc-fork-tests.txt), [java-fuji-fork-experiments.json](evidence/java-fuji-fork-experiments.json) (J14). Test source: `prototypes/solidity-foundry/test/UsdcFork.t.sol`.

## U1. Addresses (re-verified)
| Network | Address | Source |
|---|---|---|
| Fuji native USDC | `0x5425890298aed601595a70AB815c96711a31Bc65` | **DOC**: Circle [USDC contract addresses](https://developers.circle.com/stablecoins/usdc-contract-addresses) (fetched this session; states it has no financial value). Second source: a web search shows Avalanche Academy pointing to the Circle faucet for "Avalanche Fuji" and a third-party (COTI) doc listing the same address. **One official source for the address**; identity otherwise established by on-chain behaviour below. |
| Mainnet native USDC | `0xB97EF9Ef8734C71904D8002F8b6Bc66Dd9c48a6E` | **DOC**: same Circle page. |
| Mainnet USDC.e (bridged) | `0xA7D7079b0FEaD91F3e65f86E8915Cb59c1a4C664` | **No official page checked** (the address was recalled in Phase 0). On-chain it behaves as a different token (below). **Still UNVERIFIED as an official address**; do not put it in configuration except as a *deny-list* entry. |
Status: **PASS** for the two native addresses with the stated caveat; **UNVERIFIED** official source for USDC.e.

## U2. On-chain identity (OBS)
| Property | Fuji USDC | Mainnet USDC | Mainnet "USDC.e" candidate |
|---|---|---|---|
| chain id | 43113 | 43114 | 43114 |
| code size | 1,852 bytes (proxy) | 1,852 bytes | 12,716 bytes (not a proxy; EIP-1967/zeppelinos slots empty) |
| `name()` | **"USD Coin"** | "USD Coin" | **"USD Coin"** |
| `symbol()` | "USDC" | "USDC" | **"USDC.e"** |
| `decimals()` | 6 | 6 | 6 |
| `version()` | "2" | "2" | not implemented |
| `DOMAIN_SEPARATOR()` | present | present | **reverts** (no EIP-712/permit) |
| zeppelinos implementation slot | `0x6F3c4787bAB4EeEbf62eFB8C35Dc9259FDc9D9f4` (read in fork test) | `0x30dfe046...6ffe6` | empty |
| `paused()` | false | n/a | n/a |
| `totalSupply` (Fuji) | 90,076,267,137,225,547 raw (= ~90.08 billion USDC; testnet, no value) | | |
**Correction to Phase 0:** [01](../01-avalanche-technical-research.md) section 5 recorded the name as "USDC Coin". The actual `name()` is **"USD Coin"** on both native contracts and on the bridged candidate; only `symbol()` ("USDC" vs "USDC.e") and the absence of `DOMAIN_SEPARATOR` distinguish USDC.e. The distinguishing rule "never trust name or symbol; use the explicit `(chainId, address)` allow-list" stands, and is reinforced: the two tokens have the **same name**.
Also: the `implementation()` at the standard EIP-1967 slot reads zero for both native proxies because Circle's `FiatTokenProxy` uses the older zeppelinos slot. A startup guard that "checks EIP-1967" would wrongly conclude "not a proxy".

## U3. Standard calls (OBS)
`balanceOf`, `allowance`, `totalSupply`, `decimals`, `symbol`, `name` answered correctly on the real Fuji contract through web3j (J11) and Foundry. Probe address `0x...01` holds 40.533410 USDC (reused on the fork only).

## U4. Behavioural verification on a Fuji **fork** (state changes local, Fuji untouched)
All seven tests in `UsdcFork.t.sol` pass (forge 1.8.7):
| Test | Result |
|---|---|
| metadata/asset identity ("USD Coin", USDC, 6, "2") | PASS |
| `DOMAIN_SEPARATOR` equals a recomputed EIP-712 domain (`name="USD Coin"`, `version="2"`, chainId 43113, verifyingContract = USDC) | PASS |
| ERC-20 `approve` then `transferFrom` through the lab contract (`pull`), allowance consumed | PASS |
| **EIP-2612 `permit(v,r,s)`** with a valid ephemeral-key signature: sets allowance; tampered value reverts; replay reverts; `nonces` -> 1 | PASS |
| **EIP-3009 `transferWithAuthorization(v,r,s)`**: moves 1 USDC, `authorizationState` true, replay reverts, tampered nonce reverts | PASS |
| **EIP-3009 `receiveWithAuthorization`**: reverts when caller != payee, succeeds when caller == payee | PASS |
| zeppelinos implementation slot read; `isBlacklisted(holder)` callable, false | PASS |
Same via web3j (J14): fork funded from the observed holder, `approve` signed by an ephemeral web3j key (EIP-1559, chain 43113), `allowance` = 2,000,000, `Approval` log decoded, `transfer` of more than the balance reverts with `ERC20: transfer amount exceeds balance`.

**Conclusion (supersedes Phase 0 "present, not proven"):** EIP-2612 permit and EIP-3009 `transferWithAuthorization`/`receiveWithAuthorization` are **behaviourally supported** by the deployed Fuji USDC (executed, not inferred from selectors). Mainnet USDC was probed read-only only (same code size, version, DOMAIN_SEPARATOR present): **mainnet behaviour is UNVERIFIED** (Fuji's implementation address differs, so Fuji is not proof for Mainnet). The `bytes signature` overload of `transferWithAuthorization` was not exercised.

## U5. Not verified / limits
- Blacklist and pause *write* behaviour: not testable without the blacklister/pauser roles (we only read). `blacklister()` selector probe returned no result (selector may differ); **UNVERIFIED**. A blacklist/pause scenario for escrow design must be tested in Phase 3 with the fork + impersonated role holders.
- Exact FiatToken implementation version: UNVERIFIED (no source verification performed).
- Faucet: not used (see [environment-setup.md](environment-setup.md)). Reported limits conflict (a search snippet says 1 USDC / 2 h per address in Avalanche docs, another says 20 USDC / 2 h on Circle's FAQ); **UNVERIFIED**.
- The upgradeable-proxy trust assumption remains (Circle admin can upgrade/blacklist/pause).

## U6. Implication for FreelaX
1. Asset config keyed by `(chainId, address)`; startup assertions: `decimals()==6`, `symbol()=="USDC"`, `version()=="2"`, `DOMAIN_SEPARATOR()` succeeds, code size/implementation slot (zeppelinos) matches a recorded value if policy requires. Reject USDC.e (symbol `USDC.e`, no domain separator).
2. MVP funding can use `approve` + escrow `fund` (verified end to end on fork). Permit/EIP-3009 are now technically available and can be a Phase 3 design option, but remain a design decision (see [eip712-experiments.md](eip712-experiments.md) for how they differ).
3. Do not assume Mainnet equals Fuji; repeat the fork test against a Mainnet fork before any Mainnet configuration.

---
# Part B recheck (2026-11-01 14:51 UTC)

Re-fetched Circle's [USDC contract addresses](https://developers.circle.com/stablecoins/usdc-contract-addresses): Fuji address unchanged, `0x5425890298aed601595a70AB815c96711a31Bc65`; the page is undated and does **not mention USDC.e or bridged USDC for Avalanche** (only for another chain), so the USDC.e address stays **without an official source**. On-chain recheck on Fuji (publicnode; [usdc-recheck-fuji-publicnode.txt](evidence/usdc-recheck-fuji-publicnode.txt)): chain 43113, 1,852 code bytes, name "USD Coin", symbol "USDC", version "2", decimals 6, `DOMAIN_SEPARATOR` and zeppelinos implementation `0x6f3c...d9f4` identical to Part A. Native USDC and USDC.e remain distinguished by `(chainId, address)`, symbol and `DOMAIN_SEPARATOR`, never by name. **Under the simulation scope no Mainnet contact is made: the Mainnet fork test is dropped; Mainnet behaviour stays UNVERIFIED by design and is outside scope.**
