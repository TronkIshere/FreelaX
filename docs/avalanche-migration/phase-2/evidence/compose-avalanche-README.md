# Compose Avalanche evidence (LOCAL-SIM, 2026-11-05)

All runs: images built by Docker Compose (not boot jars), Anvil chain 31337, MockUSDC, throwaway secrets, no Fuji, no Mainnet, no real wallet. The wallet of `browser.json` is an
EIP-1193 test double, not MetaMask. Driver sources: `phase-2/a2-local-stack/drive.mjs`, `browser.mjs` and `phase-2/compose-avalanche/outage.mjs`; orchestration: `compose-avalanche/run.sh`.

| Folder | What it is |
|---|---|
| `compose-avalanche-20261105/` | **The result to cite:** `drive.json` PASS, `browser.json` PASS, `outage.json` PASS (scenarios O1 to O7b), one uninterrupted run on the final code |
| `compose-avalanche-20261105-run1/` | First complete run. drive PASS, browser PASS; `outage.json` FAIL at the Anvil restart: the compose Anvil container could not write its state file (it ran as an unprivileged user), so the restart reset the chain, and the gateway then answered every request with HTTP 500 (`NumberFormatException` on an empty `eth_call` answer). Both causes fixed (container user; `CHAIN_STATE_UNREADABLE`) |
| `compose-avalanche-20261105-run3/` | After those fixes. drive PASS, browser PASS; `outage.json` FAIL at O6 with the diagnostic `InvalidAuthorization()`: the gateway restart of O1 changed its ephemeral signer addresses (F3) and the script had not handed the escrow roles over again. Script fixed (it now does what an operator does) |

An earlier intermediate run (a failure caused by a mining-mode mistake in the script itself: interval mining was switched off when the manual mining was undone, so blocks stopped and a
transaction stayed CONFIRMED) was not kept. The failed folders are history, not results.
