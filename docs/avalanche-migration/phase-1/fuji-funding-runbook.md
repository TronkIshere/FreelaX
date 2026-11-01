# Runbook: fund an ephemeral Fuji test wallet and run the blocked experiments

Status of the criteria that depend on this runbook: **BLOCKED** (needs a human at a browser). Scope: [SIMULATION_SCOPE](../../SIMULATION_SCOPE.md), environment **AVALANCHE_FUJI**, testnet AVAX only. No existing wallet, key or seed phrase is ever needed, requested or touched.

## What was attempted and why it stopped
| Faucet | Finding (2026-11-01) | Result |
|---|---|---|
| Circle faucet (`faucet.circle.com`, testnet USDC) | **reCAPTCHA**, no account, one request per asset+network per 2 h, 20 USDC per address (page text). No public API mentioned. | Not automated (captcha bypass is out of bounds). |
| Avalanche console "Testnet Faucet" (`build.avax.network/console/primary-network/faucet`) | Page loads a "Checking requirements..." gate in the browser; sign-in, coupon, amount and limits are not shown without a full browser session. | Not automated. **BLOCKED: interactive action required.** |
Testnet USDC is **optional**: the experiments below use only AVAX for gas. Total AVAX needed is tiny (Fuji gas prices measured at about 160 wei per gas; the whole run needs well under 0.01 AVAX).

## Steps for the human operator
1. From `docs/avalanche-migration/phase-1/prototypes/java-web3j` (JDK 21, Maven; set `JAVA_HOME`):
   `mvn -B -ntp test -Dtest=LiveExperimentsTest -Dlive.env=AVALANCHE_FUJI -Dlive.mode=generate`
   This creates a brand-new **ephemeral testnet-only** key file at `~/.freelax-phase1/ephemeral-testnet.key` (outside the repository; override with `-Dlive.keyfile=...`) and prints **only the address**. The private key is never printed or logged.
2. Open the Avalanche faucet in a browser, request **Fuji AVAX** for that address (and optionally Circle USDC from `faucet.circle.com`, network "Avalanche Fuji"). Do **not** send anything to the address from a real-funds wallet.
3. Run the experiments:
   `mvn -B -ntp test -Dtest=LiveExperimentsTest -Dlive.env=AVALANCHE_FUJI -Dlive.mode=run`
   (optional `-Dlive.rpc=<fuji url>`; default is the official public Fuji RPC. If it answers HTTP 429, wait for `Retry-After` or pass `https://avalanche-fuji-c-chain-rpc.publicnode.com`).
4. Evidence is written to `evidence/live/AVALANCHE_FUJI-chain43113-<utc>.json` (addresses, hashes, block numbers, timestamps; no keys). Commit it only after review.
5. When finished, delete the key file (`~/.freelax-phase1/ephemeral-testnet.key`).

## What the run executes (each recorded with timings and receipt data)
E0 preflight and fee read; E1 deploy of the disposable `Phase1Lab` (timings: submit ack, first by-hash sighting, receipt, `finalized`); E2 successful call; E3 reverted call (accepted, receipt status 0); E4 duplicate submission (immediate and after mining); E5 nonce gap and a replacement-fee ladder (100/105/110/115/125/150/200 %) recording the first accepted bump; E6 response timeout after submission (a local proxy holds the send response; reconciliation by precomputed hash); E7 near-deadline inclusion, six calls `actUntil(headTimestamp + k)` for k = 0,1,2,3,5,8, recording the included block timestamp against the deadline.

## Guards built into the script (experiment-level, mirrors the specification)
- `EnvGuard.checkEndpoint` runs **before any network call**: rejects `api.avax.network` (Mainnet), loopback endpoints for Fuji, non-loopback for LOCAL_SIMULATION.
- After `eth_chainId`: rejects chain ids 43114 and 1 explicitly and anything not allow-listed for the environment.
- Refuses a signer whose Fuji balance exceeds 10 AVAX (a faucet wallet never has that much; protects against a mistakenly imported real-funds key) and a missing key file; refuses to run if below 0.01 AVAX and says to fund.
- Verified without funds: unit tests (`EnvGuardTest`), plus: a local fork pretending to be Fuji is rejected (`FUJI_FORBIDS_LOOPBACK_ENDPOINT`), a Mainnet endpoint is rejected without contact, real Fuji with no key file stops at `NO_KEY_FILE`.

## Validation done without funds (this is NOT Fuji evidence)
The same script ran end to end on **LOCAL_SIMULATION**: plain anvil (chain 31337) and a local fork of Fuji (reports chain id 43113, accepted only because the endpoint is loopback): [evidence/live/](evidence/live/). Those files prove the script works and show anvil behaviour (for example anvil accepts a 5 % replacement bump and rejects an identical-fee replacement; anvil's `finalized` tag did not reach the receipt block within 40 s). They must **not** be read as Fuji behaviour.
