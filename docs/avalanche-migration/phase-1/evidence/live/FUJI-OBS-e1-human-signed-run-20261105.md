# FUJI-OBS: Phase 1 E1 human-signed run, 2026-11-05

Label: **FUJI-OBS** (Avalanche Fuji testnet, chain id 43113, testnet AVAX only; no real funds). Not mixed with LOCAL results.
Evidence file: [AVALANCHE_FUJI-human-signed-chain43113-20261105T064313Z.json](AVALANCHE_FUJI-human-signed-chain43113-20261105T064313Z.json)
(`check_evidence.mjs`: OK, no key material, labels consistent).

## Who did what (M2)
- Signing: the **owner**, with an encrypted keystore on the owner's machine (`cast --keystore`, R1/C1 decided 2026-11-05),
  `sign_plan.sh` in the pinned Foundry 1.8.7 image. Engineering never held, saw or used the key or password.
- Broadcast: the **owner** ran `broadcast_signed.mjs` (keyless). Engineering made read-only calls only (balance, nonce, fees
  before the run) and reviewed the file.
- Sender `0xb62ca2602de72dc1edf5f6a7b614ba6376e14889` (new testnet-only wallet, nonce 0 before the run, 0.4 AVAX funded by the
  owner from another testnet wallet). Endpoint `api.avax-test.network` (allow-listed). Run 06:43:13Z to 06:57:11Z, 19 entries,
  0 RPC anomalies. Effective gas price 160 wei for every mined transaction.

## Observations (own transactions; these are Fuji facts for this run, not guarantees)
| Criterion | Observed |
|---|---|
| Deploy (#14) | lab contract `0x65c701a2946f7ebef5595888db49a16ca7f33eff` deployed, status 1; ack 621 ms, visible by hash 3.0 s, receipt 3.6 s, `finalized` 8.5 s |
| Success timing | `setValue` status 1; ack 620 ms, receipt 3.6 s, finalized 8.5 s; included 2 s (block time) after the head seen at send |
| Duplicate (E4) | the same signed bytes resent immediately: **accepted, same hash** (idempotent); resent after mining: `nonce too low: next nonce 3, tx nonce 2` |
| Revert (#18) | `boom(42)` accepted at submission, mined with **status 0** (receipt 3.6 s) |
| Nonce gap (E5) | nonce n+1 accepted while n was missing; mined only after the filler (block 60556606) |
| Equal-fee replacement | **accepted by the endpoint (hash returned) but never executed**: no receipt and no longer visible by hash after the filler; the original gap transaction mined. This is an "accepted but unexecuted" observation (E6) |
| Replacement threshold | **INCONCLUSIVE**: the ladder treats the first accepted entry as the replacement and stopped at 100 %, so 105 % to 200 % were not sent |
| Deadline rule (E7 #?) | `actUntil` matched `block.timestamp <= deadline` in **6 of 6** samples: included 1 to 2 s after the deadline reverted (k = 1, 2, 3), included 2 to 3 s before succeeded (k = 5, 8). k = 0 was sent ~16 s early (tool wait cap, fixed after the run) and is not a near-deadline sample |
| `balanceOf` (#25) | Circle test USDC `0x5425…bc65`: `callOk` true, balance 0, decimals 6 (no test USDC was requested; a zero balance still shows the call works) |
| `eth_getLogs` before execution (#16) | **INCONCLUSIVE**: one probe ran; the receipt already existed after it, so no poll happened before the receipt (Fuji included the transaction within about 2 s) |
| By-hash lag | for several transactions the by-hash lookup returned the transaction only ~3 s after acceptance, while the receipt was available about as early (load-balanced public endpoint); recorded, cause not determined |
| Finality | `finalized` reached 8.5 to 12.2 s after send for the three entries that waited for it |

## Faucet limits (E5), owner observations from screenshots, 2026-11-05
- Chainlink faucet (Fuji, 0.5 AVAX per drip): native-token requests require holding at least 1 LINK on Ethereum Mainnet.
- Avalanche Builder Hub console faucet (C-Chain, 0.5 AVAX per drip, faucet balance 196.53 AVAX at about 13:21 local): sends
  to the connected wallet only (no address field); cooldown about 24 h per account (22 h 52 m remaining when observed). The
  P-Chain faucet was available, but P-Chain AVAX is not usable for C-Chain transactions without a cross-chain transfer.

## Tool finding fixed after the run
`broadcast_signed.mjs` waited for the E7 start time with a 600-iteration cap; one iteration takes under a second, so the cap
expired before T0. Replaced by a time-bounded wait (target + 120 s) that records `waitTimedOut` as an anomaly. Network-free
tests 13/13 pass. The ladder semantics (first accepted = replaced) are recorded as a limitation, not changed.

## What this does not show
Anything about Mainnet, production RPC providers, sustained load, or the FreelaX escrow (the lab contract is disposable; the
escrow is still not deployed on Fuji, B2). Rate-limit thresholds of the public endpoint were not reached (0 anomalies).
