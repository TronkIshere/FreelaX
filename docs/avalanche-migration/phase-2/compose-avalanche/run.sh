#!/usr/bin/env bash
# Docker Compose Avalanche run (LOCAL_SIMULATION only): the images are BUILT by compose (Dockerfile.maven, Dockerfile.avalanche, frontend/Dockerfile),
# not started from boot jars. Stack: mysql, redis, Anvil (chain 31337, state kept in a volume), the contracts deployed with forge, the Avalanche
# gateway, payment, MISA, the marketplace and the Caddy frontend. Secrets are random throwaway values in a temporary env file;
# the owner's .env is never read. No Fuji, no Mainnet, no real wallet.
#
# Usage: bash docs/avalanche-migration/phase-2/compose-avalanche/run.sh <output-dir> [driver.mjs ...]
# Drivers are looked up in this folder, then in ../a2-local-stack (drive.mjs, browser.mjs). Default: drive.mjs. Each writes <output-dir>/<name>.json.
# Environment given to every driver: MARKET GATEWAY GATEWAY_KEY ANVIL OUT FRONTEND ADMIN_EMAIL ADMIN_PASSWORD STACK_PROJECT; PLAYWRIGHT_CORE passes through.
set -euo pipefail
export MSYS_NO_PATHCONV=1
HERE="$(cd "$(dirname "$0")" && pwd)"
A2="$(cd "$HERE/../a2-local-stack" && pwd)"
ROOT="$(cd "$HERE/../../../.." && pwd)"
win() { cygpath -m "$1" 2>/dev/null || echo "$1"; }
WINROOT="$(win "$ROOT")"; WINHERE="$(win "$HERE")"; WINA2="$(win "$A2")"
OUTDIR="${1:?usage: run.sh <output-dir> [driver ...]}"; shift || true
DRIVERS=("$@"); [ ${#DRIVERS[@]} -gt 0 ] || DRIVERS=(drive.mjs)
mkdir -p "$OUTDIR"; OUTDIR="$(cd "$OUTDIR" && pwd)"; WINOUT="$(win "$OUTDIR")"
P="${STACK_PROJECT:-fxav}"
FOUNDRY=ghcr.io/foundry-rs/foundry:v1.8.7
ANVIL_SENDER=0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266   # Anvil's well-known unlocked dev account (LOCAL only, no key)
MANIFEST_REL=avalanche-contracts/deployments/LOCAL_SIMULATION.json
TMPD="$(mktemp -d)"; ENVF="$TMPD/env"; ENVW="$(win "$ENVF")"

dc() { docker compose -p "$P" -f "$WINROOT/docker-compose.yml" -f "$WINHERE/docker-compose.anvil.yml" --env-file "$ENVW" --profile avalanche --profile deploy "$@"; }
getv() { grep "^$1=" "$ENVF" | head -1 | cut -d= -f2-; }

down() { dc down -v >/dev/null 2>&1 || true; }
restore_manifest() { rm -f "$ROOT/$MANIFEST_REL"; [ -f "$ROOT/$MANIFEST_REL.fxav-backup" ] && mv "$ROOT/$MANIFEST_REL.fxav-backup" "$ROOT/$MANIFEST_REL"; return 0; }
on_exit() {
  rc=$?
  if [ $rc -ne 0 ]; then
    echo "--- compose Avalanche run failed (rc=$rc); last log lines (secrets filtered):" >&2
    for s in avalanche-integration marketplace-backend; do dc logs --tail 25 "$s" 2>&1 | grep -viE "password|secret|key=" >&2 || true; done
  fi
  down; restore_manifest; rm -rf "$TMPD"; exit $rc
}
trap on_exit EXIT

# the compose file names mysql and redis containers; never touch containers of another project
for n in shared-mysql shared-redis; do
  if docker ps -a --format '{{.Names}}' | grep -qx "$n"; then
    owner="$(docker inspect -f '{{index .Config.Labels "com.docker.compose.project"}}' "$n" 2>/dev/null || true)"
    [ "$owner" = "$P" ] || { echo "container $n exists and belongs to project '${owner:-none}'; stop it first" >&2; exit 2; }
  fi
done
down
if [ -f "$ROOT/$MANIFEST_REL" ] && [ ! -f "$ROOT/$MANIFEST_REL.fxav-backup" ]; then mv "$ROOT/$MANIFEST_REL" "$ROOT/$MANIFEST_REL.fxav-backup"; fi

node "$WINHERE/mkenv.mjs" "$ENVW"
echo "[fxav] build the images (sequential)"
COMPOSE_PARALLEL_LIMIT=1 dc build avalanche-integration payment-backend misa-backend marketplace-backend frontend >"$TMPD/build.log" 2>&1 || { tail -20 "$TMPD/build.log" >&2; exit 3; }

echo "[fxav] mysql, redis, anvil"
dc up -d mysql redis anvil >/dev/null
for _ in $(seq 1 90); do [ "$(docker inspect -f '{{.State.Health.Status}}' shared-mysql 2>/dev/null)" = healthy ] && break; sleep 2; done
for _ in $(seq 1 60); do docker exec "$P-anvil-1" cast block-number --rpc-url http://localhost:8545 >/dev/null 2>&1 && break; sleep 1; done

echo "[fxav] deploy escrow + MockUSDC (chain guard in Deploy.s.sol; Anvil unlocked account, no key)"
docker run --rm --network "${P}_default" --memory 768m -v "$WINROOT/avalanche-contracts:/work" -w /work \
  -e DEPLOY_ENVIRONMENT=LOCAL_SIMULATION -e ARBITER="$ANVIL_SENDER" -e AUTH_SIGNER="$ANVIL_SENDER" "$FOUNDRY" \
  "forge script script/Deploy.s.sol --rpc-url http://anvil:8545 --unlocked --sender $ANVIL_SENDER --broadcast" >/dev/null
[ -f "$ROOT/$MANIFEST_REL" ] || { echo "deployment manifest not written" >&2; exit 4; }
ESCROW="$(grep -o '"escrow"[^,}]*' "$ROOT/$MANIFEST_REL" | grep -o '0x[0-9a-fA-F]\{40\}')"
TOKEN="$(grep -o '"token"[^,}]*' "$ROOT/$MANIFEST_REL" | grep -o '0x[0-9a-fA-F]\{40\}')"
[ -n "$ESCROW" ] && [ -n "$TOKEN" ] || { echo "no escrow or token address in the manifest" >&2; exit 4; }
sed -i "s|^AVALANCHE_PAYOUT_LOCAL_MOCK_TOKEN_ADDRESS=.*|AVALANCHE_PAYOUT_LOCAL_MOCK_TOKEN_ADDRESS=$TOKEN|" "$ENVF"

echo "[fxav] gateway (compose-built image), then the role hand-over to its ephemeral signers (F3)"
dc up -d avalanche-integration >/dev/null
SIGNERS=""
for _ in $(seq 1 120); do
  SIGNERS="$(dc logs avalanche-integration 2>&1 | grep -o 'never persisted): {[^}]*}' | head -1 || true)"
  dc logs avalanche-integration 2>&1 | grep -q "Started .*Application" && [ -n "$SIGNERS" ] && break
  sleep 2
done
ARBITER="$(echo "$SIGNERS" | grep -o 'ARBITER=0x[0-9a-fA-F]\{40\}' | cut -d= -f2)"
AUTH="$(echo "$SIGNERS" | grep -o 'AUTHORIZATION=0x[0-9a-fA-F]\{40\}' | cut -d= -f2)"
[ -n "$ARBITER" ] && [ -n "$AUTH" ] || { echo "could not read the gateway signer addresses" >&2; exit 5; }
cast() { docker exec "$P-anvil-1" cast "$@" --rpc-url http://localhost:8545; }
for addr in $(echo "$SIGNERS" | grep -o '0x[0-9a-fA-F]\{40\}'); do cast rpc anvil_setBalance "$addr" 0x56BC75E2D63100000 >/dev/null; done
AUTH_ROLE="$(docker exec "$P-anvil-1" cast keccak AUTH_SIGNER_ROLE)"
cast send --unlocked --from "$ANVIL_SENDER" "$ESCROW" "grantRole(bytes32,address)" "$AUTH_ROLE" "$AUTH" >/dev/null
cast send --unlocked --from "$ANVIL_SENDER" "$ESCROW" "setDefaultArbiter(address)" "$ARBITER" >/dev/null

echo "[fxav] payment, MISA, marketplace, frontend (compose-built images)"
dc up -d payment-backend misa-backend marketplace-backend frontend >/dev/null
for _ in $(seq 1 120); do
  code="$(curl -s -m 5 -X POST -H 'Content-Type: application/json' -d '{}' -w '%{http_code}' http://127.0.0.1:9191/api/v1/auth/sign-in 2>/dev/null | tail -c 3 || true)"
  [ "$code" = 400 ] && break; sleep 3
done
[ "$code" = 400 ] || { echo "marketplace did not become ready" >&2; exit 6; }
sleep 15   # let payment and MISA finish starting

export MARKET=http://127.0.0.1:9191 GATEWAY=http://127.0.0.1:9194 GATEWAY_KEY="$(getv AVALANCHE_INTERNAL_API_KEY)" ANVIL="$P-anvil-1" \
  FRONTEND=http://127.0.0.1:8080 ADMIN_EMAIL=admin.e2e@example.test ADMIN_PASSWORD="$(getv DEMO_ADMIN_PASSWORD)" STACK_PROJECT="$P" MYSQL_CONTAINER=shared-mysql
status=0
for d in "${DRIVERS[@]}"; do
  f="$HERE/$d"; [ -f "$f" ] || f="$A2/$d"
  [ -f "$f" ] || { echo "driver $d not found" >&2; status=7; continue; }
  name="${d%.mjs}"
  echo "[fxav] driver $d"
  OUT="$WINOUT/$name.json" node "$(win "$f")" || status=$?
done
echo "[fxav] evidence in $OUTDIR"
exit $status
