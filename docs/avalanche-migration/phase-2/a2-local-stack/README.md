# A2: single LOCAL_SIMULATION stack, end-to-end Avalanche rail

Register item **A2** (cross-service run). One running stack: MySQL 8.4, Redis 7, Anvil 1.8.7 (chain 31337), the Avalanche
gateway (JDK 21, ephemeral development signers) and the marketplace (JDK 17, Avalanche rail enabled, settlement notifications
on). `drive.mjs` exercises the real HTTP APIs and real Anvil transactions.

**LOCAL_SIMULATION only.** No Fuji, no Mainnet, no external service. The owner's `.env` is never read: every secret is a
random throwaway value created by `run.sh` for this run and discarded with the containers. Each container has a memory cap.

## What it proves (evidence: `../evidence/LOCAL_SIMULATION-a2-local-stack-20261104.json`, result PASS)
1. **A8** both parties link an EVM wallet with an EIP-191 challenge verified by the gateway; replaying a used challenge is
   refused.
2. **Fund → submit → release** with the users' own wallets: backend-prepared calldata, broadcast by the wallet, recorded by
   hash, refreshed to FINALIZED; business effects only after FINALIZED and an agreeing chain read (contract ACTIVE, job
   IN_PROGRESS; then COMPLETED / RELEASED); settlement notifications delivered.
3. **F13** on a second contract: both parties sign the SAME mutual-refund typed data, with blocks mined between the two
   signatures; the refund is sent with the held pair; REFUNDED / CANCELLED; the held signatures are spent.
4. **Phase 3 payout** (added 2026-11-04; evidence `../evidence/LOCAL_SIMULATION-a2-local-stack-payout-20261104.json`, PASS):
   the marketplace runs with `AVALANCHE_PAYOUT_PARTNER=local-mock` (labelled mock, LOCAL only) and the token from the
   manifest. After the release, the freelancer requests a payout (refused before the release, refused for the client and for
   the refunded contract 2); the freelancer's own wallet sends the released amount to the mock deposit address; the gateway
   proves the transfer (FINALIZED, exactly one matching `Transfer`); only then the mock reports a simulated fiat payout. The
   transfer hash cannot be reused. No custody: FreelaX never holds or sends the funds. See `phase-3/03-avalanche-payout-adapter.md`.

## Party wallets
The two party wallets are throwaway keys created inside the Anvil container for one run (the same pattern as the Phase 1
human-signed LOCAL validation). They are kept in the driver's memory, passed to `cast` inside that container, redacted from
every error, never written to disk and never placed in the evidence (the driver refuses to write evidence containing them).
They exist only on a disposable Anvil chain.

## F3 (ephemeral gateway signers)
The gateway verifies the deployment manifest at startup, so `run.sh` deploys first with Anvil's unlocked account as
placeholder roles (no key), starts the gateway, reads its ephemeral signer addresses from the startup log, funds them with gas,
then grants `AUTH_SIGNER_ROLE` and sets the default arbiter to those addresses (what `AvalancheApiTest` does).

## Run
```sh
# 1. boot jars (capped, one at a time)
docker run --rm --memory=2g -e MAVEN_OPTS=-Xmx768m -v "$PWD:/src" -v "$HOME/.m2:/root/.m2" -w /src \
  maven:3.9-eclipse-temurin-17 mvn -B -ntp -q -pl marketplace-backend -am -DskipTests package
docker run --rm --memory=2g -e MAVEN_OPTS=-Xmx768m -v "$PWD:/src" -v "$HOME/.m2:/root/.m2" -w /src \
  maven:3.9-eclipse-temurin-21 mvn -B -ntp -q -pl avalanche-integration -am -DskipTests package
# 2. stack + drive + teardown (about 3 minutes; about 2.5 GB of memory)
bash docs/avalanche-migration/phase-2/a2-local-stack/run.sh [evidence.json]
```
`run.sh` always tears the stack down and restores a pre-existing `deployments/LOCAL_SIMULATION.json`.

## Findings of the first runs (2026-11-04)
| Finding | Fix |
|---|---|
| An Avalanche-funded contract could not accept a work submission: `ContractSubmissionService` required a Solana escrow record or a fiat funding record, and the job stayed `AWAITING_PAYMENT` | the job follows the applied escrow outcome as on the Solana rail; the submission check accepts an Avalanche-rail milestone that reached FUNDED through the verified path (legacy rails unchanged) |
| The escrow releases only from `Submitted` (D4) | the driver submits and anchors before releasing |
| Freelancer registration requires tax and bank details | obviously fake demonstration values in the driver |
| A failed `cast` command echoed its arguments, including a throwaway key, into the log | every error is redacted; the evidence guard checks the actual generated keys |
| Harness path issues on Windows (Git Bash paths for Node) and manifest load order | fixed in `run.sh` |
