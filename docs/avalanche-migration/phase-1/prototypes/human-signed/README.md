# Human-signed Fuji experiments (Phase 1 E1): keyless tooling

Purpose: produce the Phase 1 E1 own-transaction evidence (criteria #14, #18, #25 of the
[acceptance report](../../phase-1-acceptance-report.md)) **without any backend private key or backend signing operation on
AVALANCHE_FUJI** (owner rule M2, reaffirmed 2026-11-03). It replaces the old `LiveExperimentsTest` `generate`/`run`
procedure for Fuji, which generated and used a key file and is **not** permitted under M2.

Status: tooling **IMPLEMENTED** and validated on LOCAL_SIMULATION only
([evidence](../../evidence/live/LOCAL_SIMULATION-human-signed-chain31337-20261103T150927.json)). The Fuji run is
**BLOCKED** until the owner names the funding owner, the wallet address and the signing device (decision C1).

## Separation of duties
| Step | Who | Holds a key? |
|---|---|---|
| 1. Sign the experiment transactions offline (`sign_plan.sh`) | **the human operator**, with a signer they control (for example a hardware wallet via `cast --ledger`, or an encrypted keystore via `cast --keystore`); the device is an **owner decision (C1)** | the human's signer only; `sign_plan.sh` refuses `--private-key` and `--mnemonic` |
| 2. Broadcast the already-signed bytes and record observations (`broadcast_signed.mjs`) | the operator or a developer | **no**: the tool has no key input; a plan line with a field named like key/mnemonic/seed/secret/password is refused |
| 3. Review and commit the evidence JSON | a developer | no (addresses, hashes, timings only) |

## Guards (fail closed, before any request)
`broadcast_signed.mjs` mirrors the gateway's `EnvironmentGuard`: explicit environment; Mainnet hosts and URLs rejected;
Fuji only over https to the two allow-listed hosts (`api.avax-test.network`, `avalanche-fuji-c-chain-rpc.publicnode.com`;
O-8 stays DEFERRED, the list is not extended); LOCAL_SIMULATION only on local-class hosts; every signed transaction must be
EIP-1559 and signed for an allow-listed chain id (43114, 1, 0 refused); then `eth_chainId` and the client family
(`anvil/` or `avalanchego/`) are checked; a sender holding more than 10 AVAX on Fuji stops the run (possible real-funds
wallet). Network-free tests: `node --test broadcast_signed.test.mjs check_evidence.test.mjs` (5 + 6 tests).

## What the plan executes
| Entry | Experiment | Needs |
|---|---|---|
| `E1-deploy` | deploy the disposable `Phase1Lab` (bytecode `../java-web3j/src/test/resources/Phase1Lab.bin`): submit ack, by-hash, receipt, `finalized` timings | AVAX |
| `setup-grant-operator` | grant `OPERATOR_ROLE` to the sender (needed by `setValue`) | AVAX |
| `E2-success+E4-duplicate` | successful call with timings; the same signed bytes resent immediately and after mining | AVAX |
| `E3-revert` | `boom(42)`: accepted at submission, executed with receipt status 0 | AVAX |
| `E5-gap`, `E5-bump-*`, `E5-filler` | nonce gap, replacement ladder 100 to 200 % (stops at the first accepted bump), filler | AVAX |
| `E7-k0..k8` | `actUntil(T_i + k)` broadcast when the head reaches `T_i`; records the including block timestamp against the deadline | AVAX |
Not covered: the Phase 1 E6 "timeout after submission" fault (needs a local proxy; covered on LOCAL by gateway test S3).
**Circle test USDC is not needed** for any entry above: they touch only the lab contract. Test USDC is needed only for
experiments that move tokens (escrow funding or settlement on Fuji, register B2 to B4), which are out of this plan.
AVAX required: the gas limits of the transactions that can execute add up to about 4.7 M; at the Phase 1 Fuji gas price
(about 160 wei per gas) that is far below 0.01 AVAX. Admission also requires the sender to afford gasLimit x maxFee per
transaction, so sign with the live fee values (never hard-coded) and fund comfortably above that worst case.

## Procedure (only after the owner has confirmed funding owner, address and signing device)
1. Read the sender's nonce and current fees (read-only): `cast nonce <SENDER> --rpc-url <allow-listed Fuji URL>`,
   `cast base-fee ...`; choose `T0` a few minutes ahead (`date +%s` + 300).
2. Sign: `SIGNER_FLAGS="--ledger" SENDER=<addr> NONCE=<n> CHAIN_ID=43113 MAX_FEE=<wei> PRIORITY=<wei> T0=<unix> RPC=<url> sh sign_plan.sh`
   (writes `plan.jsonl`; broadcast promptly so the E7 samples start on time; keep `SPACING` at the default 60 s on Fuji).
3. Broadcast: `node broadcast_signed.mjs AVALANCHE_FUJI <allow-listed Fuji URL> plan.jsonl evidence.json` (Node >= 22).
4. Rename `evidence.json` to `AVALANCHE_FUJI-human-signed-chain43113-<utc>.json` and check it **before** committing:
   `node check_evidence.mjs AVALANCHE_FUJI-human-signed-chain43113-<utc>.json` (added 2026-11-04; fails on key-like field
   names, 32-byte hex outside `*hash` fields, PEM blocks, mnemonic-like word lists, Mainnet or mismatched environment, chain
   id, client family or file name, and on an unfinished run). Only on `OK`, move it to `../../evidence/live/` and commit;
   delete `plan.jsonl`. A human still reads the `anomalies` and `stopped` fields.

## Local validation (LOCAL_SIMULATION, 2026-11-03)
Anvil 1.8.7 (`--block-time 1`) in Docker; a throwaway keystore created inside a disposable container (LOCAL_SIMULATION
development signer, permitted by M2) signed the plan; the keyless broadcaster then ran all 19 entries. Observed (anvil only,
**not** Fuji behaviour): deploy, role grant and success mined; immediate resend `transaction already imported`, resend after
mining `nonce too low`; the revert was accepted and mined with status 0; the gap transaction was accepted, the equal-fee
replacement was `replacement transaction underpriced`, the **5 %** bump was accepted and mined after the filler; all six E7
samples were consistent with `blockTimestamp <= deadline` (k = 0 reverted, included 1 s after its deadline).
