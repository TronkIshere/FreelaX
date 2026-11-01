# Phase 1 - Fuji C-Chain network and RPC validation

Run: 2026-11-01 ~13:15-13:40 UTC. Network: Avalanche Fuji C-Chain. Evidence: [fuji-probe-official.json](evidence/fuji-probe-official.json), [fuji-probe-publicnode.json](evidence/fuji-probe-publicnode.json), [java-fuji-fork-experiments.json](evidence/java-fuji-fork-experiments.json) (J11-J13). Script: `scripts/fuji_probe.py`.

## Source tags
**DOC** official documentation read this session; **OBS** directly observed; **REC** recommendation.

## F1. Endpoints and chain id
| Item | Result | Tag |
|---|---|---|
| Official Fuji C-Chain RPC | `https://api.avax-test.network/ext/bc/C/rpc` is listed on the Avalanche Fuji quick-start page. The C-Chain RPC page lists only Mainnet/local endpoints, **no Fuji URL**. | DOC |
| `eth_chainId` / `net_version` | `0xa869` / `43113` on official and on publicnode (`https://avalanche-fuji-c-chain-rpc.publicnode.com`) | OBS |
| Other providers tried | `rpc.ankr.com/avalanche_fuji` requires an API key (rejected: "must authenticate"); `avalanche-fuji.drpc.org` answered `eth_chainId` only (not probed further) | OBS |
| Status | **PASS** (connectivity, chain id). Two providers compared; "reputable provider" for publicnode is my judgement, not documented. | |

## F2. Method coverage (both providers unless noted)
`eth_chainId`, `eth_blockNumber`, `eth_getBlockByNumber` (latest/safe/finalized/pending), `eth_getBalance`, `eth_gasPrice`, `eth_feeHistory`, `eth_getTransactionCount`, `eth_getCode`, `eth_getStorageAt`, `eth_call`, `eth_getLogs`, `eth_getTransactionByHash`, `eth_getTransactionReceipt` all returned results. Avalanche-specific `eth_baseFee` and `eth_suggestPriceOptions` worked on the official endpoint (`eth_baseFee` = `0xa` = 10 wei, `eth_gasPrice` = `0xa0` = 160 wei, `eth_suggestPriceOptions` normal tip `0x96` = 150 wei).
Note: these Fuji fees are tiny and are a snapshot, not a model. Never hard-code fees (DOC: dynamic).

## F3. Block header under Helicon (OBS)
Latest block on the official endpoint carries 36 fields including Helicon/ACP-176/226 additions: `timestampMilliseconds`, `minDelayExcess`, `settledHeight`, `settledGasNumerator`, `settledGasUnix`, `settledExcess`, `targetExponent`, `minPriceExponent`, `blockGasCost`, `extDataHash`. **Confirms the Phase 0 claim that ACP-226 millisecond timestamps are live on Fuji.** `safe` = `finalized` (one block behind `latest` at probe time; 4-5 blocks behind in the longer observation), `pending` = `latest`.

## F4. Latency
- Probe via curl subprocess: `eth_blockNumber` n=60, official median 380 ms (min 328, max 734); publicnode median 389 ms (324-868). **Dominated by process spawn + TLS from this host**; not a provider benchmark.
- Node `fetch` (persistent connections), `eth_getTransactionReceipt` n=53: min 216, median 240, max 426 ms. This is the more representative figure for this host. It says nothing about other regions.
- No claim is made that either provider is faster.

## F5. Rate limiting
- 60 sequential `eth_blockNumber` requests in 24-25 s (~2.5 req/s): **60/60 HTTP 200** on both providers. WebSocket + ~100 HTTP calls in 90 s: no errors.
- No 429 was seen. **Limits were not probed beyond this** (deliberately gentle on public endpoints). Public limits remain **undocumented/UNVERIFIED** (neither doc page states any).

## F6. `eth_getLogs` limits (the Phase 0 hypothesis of 2,000 blocks) - important
| Provider | Max block range observed | Address required? | Error |
|---|---|---|---|
| Official Fuji public RPC | **100 blocks** (99-100 ok; 101 fails) | No (3 logs returned without address over 99 blocks) | `-32602 block range too large: total block range in this request exceeds the maximum of 100 blocks` |
| publicnode | **50,000 blocks** (50,000 ok, 100,000 fails) | **Yes** (without address: `-32701 Please specify an address ...`) | `-32701 exceed maximum block range: 50000` |
Also: publicnode returned an empty (successful) result for a 100-block window 1,000,000 blocks back; the official endpoint refused any range >100 so historical depth was not probed there (UNVERIFIED).
**Verdict: Phase 0 hypothesis "2,000-block chunks" FAILS on the official public endpoint and is merely sub-limit on publicnode.** Chunk size must be a per-provider configuration with a conservative default of 100. See [rpc-reconciliation-evaluation.md](rpc-reconciliation-evaluation.md).

## F7. Failure behaviour
Via web3j ([J13](evidence/java-fuji-fork-experiments.json)): unroutable host -> `SocketTimeoutException` after 2,019 ms (2 s timeout); closed port -> `ConnectException`; unknown tx hash and unknown receipt -> **JSON `null` result, not an error**; `eth_call` revert surfaces as error code 3 `execution reverted: ERC20: transfer amount exceeds balance`.

## F8. Rejection at submission (no cost; no funds used)
A signed EIP-1559 USDC `transfer` from a fresh ephemeral key with zero balance was submitted to Fuji:
- correct chain id: **rejected** `-32000 insufficient funds for gas * price + value: balance 0, tx cost 1800000000000000` (cost = gasLimit 60,000 x maxFee 30 gwei). This is the Helicon **acceptance check** (sender must afford the worst case) observed from outside.
- signed for chain id 1: **rejected** `-32000 invalid sender` (EIP-155 chain binding works).
- nonce of the unfunded address stayed 0.
Nothing reached the chain.

## F9. Reliability observations
- One inconsistency: during the 53-transaction observation, **one** accepted transaction (`0x8c719fb053...`) was **not found by `eth_getTransactionByHash`** at the instant its receipt was already available (receipt status 0x1, block 0x398e8be). Other 52 found. Cause unknown (possibly different backend nodes behind the public load balancer). Implication: a null by-hash result is not proof of absence. Not reproduced in the later 14-transaction run (0 misses).
- Provider head heights differed by 0-2 blocks and `finalized` by 2 blocks between providers at the same moment (expected skew).

## F10. Status table
| Check | Status |
|---|---|
| Connectivity, chain id 43113 | PASS |
| Required methods | PASS |
| Latency measured | PASS (host-specific, curl-biased for probe) |
| Rate limits | **INCONCLUSIVE** (no throttling at ~2.5 req/s; limits unknown) |
| Log range | PASS (measured: 100 official / 50,000 publicnode) |
| Second provider compared | PASS (publicnode; limited scope) |
| Funded-account balance/nonce call | PARTIAL: `eth_getBalance`/nonce work on any address; no funded wallet |
| Receipt/log retrieval | PASS (on others' transactions) |
