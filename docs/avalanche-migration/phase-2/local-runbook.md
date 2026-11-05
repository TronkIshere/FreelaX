# Local runbook: start and validate FreelaX from a clean checkout (LOCAL_SIMULATION)

Everything here is local: Docker, throwaway secrets, Anvil chain 31337 with MockUSDC. No Fuji, no Mainnet, no real wallet, no owner `.env`.
Commands are for Git Bash on Windows with Docker running (adapt paths elsewhere). `W` is the repository root as a Windows path.

```sh
cd /path/to/FreelaX
export MSYS_NO_PATHCONV=1; W="$(pwd -W 2>/dev/null || pwd)"
```

## 0. Prerequisites actually used
Docker (29.x), Git Bash, Node on the host only for the harness drivers (22.10 is enough), Microsoft Edge for the browser run. Java and the
frontend toolchain run inside pinned images: `maven:3.9-eclipse-temurin-17` (legacy modules), `maven:3.9-eclipse-temurin-21` (gateway),
`node:24.19.0` (frontend), `ghcr.io/foundry-rs/foundry:v1.8.7` (contracts), `mysql:8.4`, `redis:7`. Do not install toolchains globally.

## 1. Configuration (no undocumented values)
`.env.example` lists every variable; copy it to an ignored `.env` and fill the blanks, or use a throwaway file. Rules that a blank value hides:
- the 11 variables that compose marks as required (`${VAR:?...}`) must be set (`node docs/avalanche-migration/phase-2/validate_env.mjs` checks this and
  that `docker compose config` renders);
- the three JWT secrets need at least 64 characters (HS512); demo passwords need 24 to 72 characters (bcrypt);
- `AVALANCHE_ENVIRONMENT` has no default (`LOCAL_SIMULATION` or `AVALANCHE_FUJI`; Mainnet is refused);
- the Avalanche rail of the marketplace and the browser panel are off by default (`AVALANCHE_GATEWAY_ENABLED=false`, `VITE_AVALANCHE_ENABLED=false`).

`validate_env.mjs` never reads `.env`; it builds a temporary env file from the placeholders plus random values and deletes it.

## 2. Legacy modules and the frontend through compose
```sh
docker compose --env-file <your env file> --profile deploy up -d --build mysql redis payment-backend misa-backend solana-integration marketplace-backend frontend
```
Wait until `POST http://127.0.0.1:9191/api/v1/auth/sign-in` answers (400 for an empty body). The services restart by themselves while MySQL initializes,
so first start takes a few minutes. If you change `MYSQL_ROOT_PASSWORD` after the volume exists, run `docker compose ... down -v` first.
The project's own end-to-end scripts (`scripts/partner-mock-e2e.mjs`, `partner-mock-admin-e2e.mjs`) read the repository `.env`; run them from a copy next to
a `.env` that matches the stack you started.

## 3. Avalanche path on one local stack (gateway, Anvil, contracts, marketplace)
The compose `avalanche` profile needs an Anvil node, a deployment manifest and a role hand-over to the gateway's ephemeral signers (F3); the harness
does all of it with disposable containers:
```sh
# build the boot jars once (clean build: a stale class in target/ has broken a start before)
docker run --rm --memory=2g -e MAVEN_OPTS=-Xmx768m -v "$W:/src" -v "$HOME/.m2:/root/.m2" -w /src maven:3.9-eclipse-temurin-17 mvn -B -ntp -q -pl marketplace-backend -am -DskipTests clean package
docker run --rm --memory=2g -e MAVEN_OPTS=-Xmx768m -v "$W:/src" -v "$HOME/.m2:/root/.m2" -w /src maven:3.9-eclipse-temurin-21 mvn -B -ntp -q -pl avalanche-integration -am -DskipTests package
# API-level end to end: wallet link, fund, submit, release, payout, mutual refund, stuck escrow, mutual settlement, administrator decision, arbiter
bash docs/avalanche-migration/phase-2/a2-local-stack/run.sh "D:/path/to/evidence.json"
```
The evidence path must be given as `D:/...` on Windows. The run takes about 6 minutes and tears everything down.

## 4. Browser validation of the real UI (wallet is a test double)
```sh
mkdir -p /tmp/pw && (cd /tmp/pw && npm init -y >/dev/null && npm i playwright-core@1.49.1 --no-audit --no-fund)
A2_FRONTEND=1 A2_DRIVER="D:/path/to/FreelaX/docs/avalanche-migration/phase-2/a2-local-stack/browser.mjs" \
  PLAYWRIGHT_CORE="/tmp/pw/node_modules/playwright-core/index.mjs" \
  bash docs/avalanche-migration/phase-2/a2-local-stack/run.sh "D:/path/to/browser-evidence.json"
```
Uses the installed Microsoft Edge (`channel: msedge`); nothing is downloaded system-wide.

## 5. Contracts, gateway, frontend, tooling suites
See [validation-matrix.md](../validation-matrix.md) "Commands of the final validation phase".

## 6. Phase 1 evidence helpers (read-only)
- `node docs/avalanche-migration/phase-1/evidence/review_e1_evidence.mjs`: checks the E1 Fuji evidence file against the FUJI-OBS report (no network).
- `sh docs/avalanche-migration/phase-1/scripts/db_query_synthetic.sh <out.txt>`: the six E2 queries against a disposable MySQL with synthetic data (not E2 evidence).

## 7. Teardown
`docker compose -p <project> down -v` for step 2; steps 3 and 4 remove their containers and restore any local manifest by themselves.
Containers named `shared-mysql` and `shared-redis` are created by compose; check `docker ps` before removing anything you did not start.
