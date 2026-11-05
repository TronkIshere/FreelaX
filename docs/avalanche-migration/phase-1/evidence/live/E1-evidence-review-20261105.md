# E1 evidence review (2026-11-05)

Label: **review of FUJI-OBS evidence** (no new Fuji data; no network access). Reviewed file:
`AVALANCHE_FUJI-human-signed-chain43113-20261105T064313Z.json`, report: [FUJI-OBS-e1-human-signed-run-20261105.md](FUJI-OBS-e1-human-signed-run-20261105.md).
Method: `phase-1/evidence/review_e1_evidence.mjs` re-checks every checkable claim of the report against the JSON file (re-runnable, read-only).

## Result
24 claims SUPPORTED, 0 NOT SUPPORTED, 1 NOT CHECKABLE (the boundary statement "this says nothing about the FreelaX escrow", a documentation
statement). Integrity: label and host consistent (AVALANCHE_FUJI, 43113, allow-listed host), 13 unique 32-byte transaction hashes, 19 entries,
run window 06:43:13Z to 06:57:11Z, 0 recorded RPC anomalies, sender nonces 0 to 11 contiguous, all block timestamps inside the window,
effective gas price 160 wei on every mined transaction, no key material in the file.

## Observations about the documentation (no claim was found false)
1. The report's table row "Deadline rule (E7 #?)" carries a stray `#?` label. Cosmetic.
2. "6 of 6 samples" counts `E7-k0`, which the report itself says is not a near-deadline sample (sent about 13 s early). The rule held in all 6, and
   in the 5 near-deadline samples (k1, k2, k3 included after the deadline and reverted; k5, k8 included before it and succeeded). Read it as
   "5 near-deadline samples plus 1 early one".
3. The replacement-threshold result (INCONCLUSIVE) is correctly stated: the ladder entries 105 to 200 % were skipped by the tool's design.
4. Nothing in the file or the report presents LOCAL_SIMULATION data as Fuji data; the LOCAL files in the same folder carry their own labels.

## What this evidence does not show
Anything about the FreelaX escrow on Fuji (not deployed), Mainnet, other RPC providers, sustained load, or the rate-limit threshold of the public
endpoint (0 anomalies, so no threshold was reached). E1 acceptance by the owner is still pending; this review is not that acceptance.

## Script output
```
E1 evidence review of live/AVALANCHE_FUJI-human-signed-chain43113-20261105T064313Z.json
SUPPORTED     | label and network: AVALANCHE_FUJI, chain 43113, allow-listed host | AVALANCHE_FUJI 43113 api.avax-test.network
SUPPORTED     | no key material in the file | pattern scan of the text, transaction hashes excluded
SUPPORTED     | every transaction hash is 32 bytes and unique | 13 hashes
SUPPORTED     | obs.hash equals the entry hash | 
SUPPORTED     | 19 entries | 19
SUPPORTED     | run window 06:43:13Z to 06:57:11Z and 0 RPC anomalies | 2026-11-05T06:43:13.158Z .. 2026-11-05T06:57:11.716Z, anomalies 0
SUPPORTED     | sender nonce starts at 0 and the deploy is nonce 0 | nonces 0,1,2,3,4,5,6,7,8,9,10,11
SUPPORTED     | every block timestamp lies inside the run window (plus 15 s of clock tolerance) | 1793860997..1793861829 vs 1793860993..1793861831
SUPPORTED     | effective gas price is 160 wei (0xa0) for every mined transaction | 11 mined entries
SUPPORTED     | deploy: status 1, contract address recorded, ack 621 ms, receipt 3.6 s, finalized 8.5 s | ack 621, receipt 3639, finalized 8493
SUPPORTED     | success: status 1, finalized 8.5 s, included 2 s after the head seen at send | finalized 8473, block minus head 2
SUPPORTED     | duplicate: immediate resend accepted with the same hash | ACCEPTED 0x921b626964bfa11de88434c596e1c11c24f0d9b8e723c5f1c76b6cd762b77a4c
SUPPORTED     | duplicate: resend after mining refused with "nonce too low: next nonce 3, tx nonce 2" | nonce too low: next nonce 3, tx nonce 2
SUPPORTED     | revert: accepted at submission and mined with status 0 (receipt 3.6 s) | status 0x0, receipt 3617
SUPPORTED     | nonce gap: nonce n+1 accepted while n missing, mined only with the filler (same block 60556606) | gap nonce 5, filler nonce 4, blocks 60556606/60556606
SUPPORTED     | equal-fee replacement accepted (hash returned) but never executed and not visible by hash | visibleByHash false
SUPPORTED     | replacement threshold INCONCLUSIVE: 105 to 200 % not sent | 6 skipped: E5-bump-105,E5-bump-110,E5-bump-115,E5-bump-125,E5-bump-150,E5-bump-200
SUPPORTED     | deadline rule: block.timestamp <= deadline matched in 6 of 6 samples | 6 of 6
SUPPORTED     | deadline: entries included after the deadline all reverted, entries at or before it all succeeded | after E7-k1:2 E7-k2:1 E7-k3:1; before E7-k0:-13 E7-k5:-2 E7-k8:-3
SUPPORTED     | k0 was sent about 16 s early (not a near-deadline sample) | includedMinusDeadline -13
SUPPORTED     | balanceOf: callOk true, balance 0, decimals 6, pinned Circle test USDC | {"token":"0x5425890298aed601595a70ab815c96711a31bc65","holder":"0xb62ca2602de72dc1edf5f6a7b614ba6376e14889","callOk":true,"balance":"0","decimals":6,"error":null}
SUPPORTED     | eth_getLogs before execution INCONCLUSIVE: no poll happened before the receipt | {"address":"0x65c701a2946f7ebef5595888db49a16ca7f33eff","fromBlock":"0x39c052a","polls":1,"pollsBeforeReceipt":0,"nonEmptyPollsBeforeReceipt":0,"errors":0}
SUPPORTED     | by-hash lag of about 3 s on several transactions | 7 entries with byHashMs 2.0-3.1 s
SUPPORTED     | finalized reached 8.5 to 12.2 s after send for the three entries that waited | 8493, 8473, 12197 ms
NOT CHECKABLE | finality in this run says nothing about FreelaX escrow (lab contract only) | documentation boundary, not checkable from the file

25 claims: 24 supported, 0 not supported, 1 not checkable
```
