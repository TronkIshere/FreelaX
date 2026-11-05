# Compose Avalanche evidence (LOCAL-SIM, 2026-11-05)

Scope of every run below: images built by Docker Compose (not boot jars), Anvil chain 31337, MockUSDC, throwaway secrets, **LOCAL_SIMULATION only**: no Fuji, no Mainnet, no real wallet.
The wallet of `browser.json` is an EIP-1193 **test double**, not MetaMask. The payout step of `drive.json` runs through the **LOCAL mock payout partner**
(`AVALANCHE_PAYOUT_PARTNER=local-mock`, simulated fiat confirmation); there is no real payout provider. Driver sources: `phase-2/a2-local-stack/drive.mjs`,
`phase-2/a2-local-stack/browser.mjs`, `phase-2/compose-avalanche/outage.mjs`; orchestration: `phase-2/compose-avalanche/run.sh`.

How to read this page: statements are tagged **[recorded]** (visible in the JSON files in this repository), **[experiment]** (observed by the engineer in a separate diagnostic run
or command whose raw output was **not committed**, so it cannot be re-checked from the repository) or **[inferred]** (an explanation that fits the observations but was not proven).
The JSON files of the failed runs are kept unchanged as history. They are not results.

## The result to cite
`compose-avalanche-20261105/`: `drive.json` PASS (45 steps), `browser.json` PASS (15 steps), `outage.json` PASS (steps: setup, O1 to O7 and O7b), one uninterrupted run,
started 2026-11-05T13:24:49Z, last file finished 13:28:26Z **[recorded]** (the timestamps are in the JSON files).

## The three folders
| Folder | drive | browser | outage | Run window (UTC) [recorded] |
|---|---|---|---|---|
| `compose-avalanche-20261105/` | PASS | PASS | **PASS** | 13:24:49 to 13:28:26 |
| `compose-avalanche-20261105-run3/` | PASS | PASS | **FAIL** | 13:10:50 to 13:15:39 |
| `compose-avalanche-20261105-run1/` | PASS | PASS | **FAIL** | 12:21:35 to 12:25:24 |

`drive.json` and `browser.json` say PASS in the failed folders; those verdicts belong to the code of that run and do not make the folder a result.
There was also one earlier attempt that stopped before any driver ran (a shell error in the readiness wait of `run.sh`) and left no evidence, and a third folder
(`-run2`, outage FAIL at O6) that was deleted before any commit; see "Not in the repository".

## `-run1` (failed) 
- **[recorded]** `outage.json` has the steps `setup`, `O1`, `O2` and then `result: FAIL`, `error`: "timeout waiting for escrow still funded after the chain restart", last observation
  `GET /api/v1/contracts/<id>/avalanche -> 500`. The file does not contain a cause.
- **[experiment, not committed]** The failure console output of `run.sh` showed the gateway logging `unexpected failure: NumberFormatException` for every request after the Anvil restart.
  A separate local test with the same Anvil flags (`--state`, `--state-interval 2`) and a fresh named volume showed that no state file was written when the container ran as its default
  unprivileged user, and that a state file was written and the chain (code and block number) survived `docker restart` when run with `--user 0:0`.
- **[inferred]** Chain state was lost at the restart, `eth_call` then returned `0x` for the escrow address, and the gateway parsed that empty value as a number. This chain of causes was
  not observed end to end in `-run1` (its Anvil state was not inspected). Fixes: the compose override runs Anvil as root (`docker-compose.anvil.yml`); `UnreadableChainStateException` is mapped to
  HTTP 502 `CHAIN_STATE_UNREADABLE` (unit test `EscrowReaderEmptyAnswerTest` covers the empty-answer parsing, not the full scenario).

## `-run3` (failed)
- **[recorded]** `outage.json` has the steps `setup`, `O1` to `O5` and then `result: FAIL`, `error`: "timeout waiting for pending transaction applied after mining" with the diagnostics
  `rows: [{status: REVERTED, applied: false}]`, `chain: null`, `contract: PENDING_FUNDING/PENDING_FUNDING`, `chainBlock: 403`. **The file does not contain `InvalidAuthorization()` or any revert reason.**
  It also lacks the field `rolesHandedOverToTheNewEphemeralSigners` that the final run's O1 step records.
- **[experiment, not committed]** A later partial run of the scenarios O1 to O4 and O6 with an added trace of the reverted transaction returned the revert selector `0x2ce87eeb`, which matched
  `InvalidAuthorization()` among the escrow errors tried by name. Other partial runs gave: O6+O7: O6 passed and the retry at O7b timed out; O7 alone: passed; O5+O6+O7: O6 passed and O7b timed out with the
  retry's funding row in `CONFIRMED` (receipt status 1) while the chain read FUNDED.
- **[inferred]** After O1 restarts the gateway, its LOCAL signer addresses change (the documented limitation F3), the escrow roles still pointed at the old addresses, so every authorization the new gateway
  signed was refused on chain. The script now hands the roles over after the restart (like `run.sh` does at start). Separately, the stuck `CONFIRMED` row in the partial run is **inferred** to come from
  Anvil's mining modes: the script enabled interval mining and then automine, and in Anvil the later call replaces the earlier mode, so blocks stopped and LOCAL finality (confirmations) was never reached.
  The block production in that failed state was not measured. The fix (restore automine first, interval mining last, and check that blocks advance) is covered by the final run, which passes.

## Not in the repository
- Raw output of the partial diagnostic runs (O6+O7, O7, O5+O6+O7, and O1 to O4 with O6) and of the separate Anvil persistence test.
- `-run2`: an outage FAIL at O6 with the same message as `-run3` but without diagnostics. Its JSON was deleted before any commit; whether its cause was the same is **[inferred]** from the identical symptom.
- The console output of `run.sh` and the build logs of every run (they were written to a temporary directory that the script deletes).

## Provenance of the final run: what can and cannot be established
The evidence files record no git commit, no image id and no image digest. **They do not establish, cryptographically or otherwise, which source commit or which image digests produced them.**
What the repository does allow (all **[recorded]** facts, plus reasoning from them):
- Application code of this work was committed at 2026-11-05T19:45:13+07:00 (= 12:45:13Z): `c4d0806`, `3abdeda`, `69af645`. The final run started at 13:24:49Z, after those commits.
- `147d3c3` (compose pacing variables, `.env.example`, validator) and `ed34acc` (the compose-avalanche scripts, `browser.mjs`, these evidence folders) were committed at 20:30:14+07:00 (= 13:30:14Z),
  **after** the final run finished (13:28:26Z). So the compose file, the orchestration scripts and the drivers ran as uncommitted working-tree files and were committed afterwards. That they are byte-identical to the
  committed versions is **not verifiable** from the artifacts; the engineer reports that no change was made to them between the run and the commit (this is not recorded anywhere in the repository).
- The final `outage.json` contains `rolesHandedOverToTheNewEphemeralSigners`, a field that exists in the committed `outage.mjs` (it contains `handOverRoles`); `-run1` and `-run3` lack it. This ties the final
  run to the outage script version that includes the F3 hand-over, not to a commit.
- On the machine where the runs were made, the locally built compose images for the marketplace, payment, gateway and frontend carry creation times of 2026-11-05 19:46 +07:00 (= 12:46Z), about a minute
  after the application commits and before every evidence run except `-run1`; the final run reused them (compose cache). The images are not part of this repository and no run recorded their ids, so this is
  consistent with, not proof of, the claim "built from `c4d0806`, `3abdeda`, `69af645`".
Anyone who needs a verifiable chain should re-run `run.sh` on a clean checkout of a named commit and keep its build log and `docker image inspect` output next to the JSON files.
