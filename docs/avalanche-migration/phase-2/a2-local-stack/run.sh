#!/usr/bin/env bash
# Register item A2: ONE running LOCAL_SIMULATION stack (MySQL, Redis, Anvil, Avalanche gateway, marketplace) and an end-to-end
# drive of the Avalanche rail through the real HTTP APIs (drive.mjs). LOCAL_SIMULATION only: chain 31337, MockUSDC, no Fuji,
# no Mainnet, no external network calls except none. Self-contained: it never reads the owner's .env; every secret is a random
# throwaway value generated for this run and discarded with the containers. Containers have memory caps (this machine: 8 GB
# Docker VM). F3 is handled as the gateway's own tests do: start the gateway, read its ephemeral signer addresses from the
# log, deploy with those as arbiter and authorization signer, and let the gateway load the manifest lazily.
#
# The dev-profile seed administrator (admin.e2e@example.test, random throwaway password, discarded with the containers) stands for
# the arbiter role: the administrator decides the dispute and triggers the gateway's ephemeral LOCAL arbiter signer.
# Prerequisites: Docker running; boot jars built (marketplace on JDK 17, gateway on JDK 21; see README.md); Node >= 22.
# Usage: bash docs/avalanche-migration/phase-2/a2-local-stack/run.sh [evidence-output.json]
set -euo pipefail
export MSYS_NO_PATHCONV=1
HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$HERE/../../../.." && pwd)"
WINROOT="$(cd "$ROOT" && pwd -W 2>/dev/null || pwd)"
WINHERE="$(cd "$HERE" && pwd -W 2>/dev/null || pwd)"   # Node on Windows needs D:/... rather than Git Bash's /d/...
OUT="${1:-$WINHERE/../evidence/a2-local-stack-$(date -u +%Y%m%dT%H%M%SZ).json}"
P=freelax-a2
NET=$P-net
FOUNDRY=ghcr.io/foundry-rs/foundry:v1.8.7
GW_JAR=avalanche-integration/target/avalanche-integration-0.1.0.jar
MK_JAR=marketplace-backend/target/backend-0.1.0.jar
MANIFEST_REL=avalanche-contracts/deployments/LOCAL_SIMULATION.json
ANVIL_SENDER=0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266   # Anvil's well-known unlocked dev account (LOCAL only)

rnd() { openssl rand -hex "$1"; }
DB_PW="a2db-$(rnd 12)"; JWT="$(rnd 48)"; GW_KEY="a2gw-$(rnd 24)"; MISA_PW="A2misa-$(rnd 12)!"
DEMO_C="A2client-$(rnd 8)!Aa1"; DEMO_F="A2freelancer-$(rnd 8)!Aa1"; DEMO_A="A2admin-$(rnd 12)!Aa1-throwaway"

down() {
  for c in marketplace gateway anvil redis mysql; do docker rm -f "$P-$c" >/dev/null 2>&1 || true; done
  docker network rm "$NET" >/dev/null 2>&1 || true
}
restore_manifest() {
  # A LOCAL manifest that existed before this run (gitignored, disposable) is put back as it was.
  rm -f "$ROOT/$MANIFEST_REL"
  [ -f "$ROOT/$MANIFEST_REL.a2-backup" ] && mv "$ROOT/$MANIFEST_REL.a2-backup" "$ROOT/$MANIFEST_REL"
  return 0
}
logs_on_failure() {
  echo "--- A2 failed; last gateway and marketplace log lines:" >&2
  docker logs --tail 40 "$P-gateway" 2>&1 | grep -viE "password|secret|key=" >&2 || true
  docker logs --tail 60 "$P-marketplace" 2>&1 | grep -viE "password|secret|key=" >&2 || true
}
trap 'rc=$?; [ $rc -ne 0 ] && logs_on_failure; down; restore_manifest; exit $rc' EXIT

for jar in "$GW_JAR" "$MK_JAR"; do [ -f "$ROOT/$jar" ] || { echo "missing $jar (build it first, see README.md)" >&2; exit 2; }; done
down
if [ -f "$ROOT/$MANIFEST_REL" ] && [ ! -f "$ROOT/$MANIFEST_REL.a2-backup" ]; then mv "$ROOT/$MANIFEST_REL" "$ROOT/$MANIFEST_REL.a2-backup"; fi
docker network create "$NET" >/dev/null

echo "[a2] mysql, redis, anvil"
docker run -d --name "$P-mysql" --network "$NET" --memory 768m -e MYSQL_ROOT_PASSWORD="$DB_PW" \
  -v "$WINROOT/init-db.sql:/docker-entrypoint-initdb.d/init.sql:ro" mysql:8.4 >/dev/null
docker run -d --name "$P-redis" --network "$NET" --memory 128m redis:7 >/dev/null
docker run -d --name "$P-anvil" --network "$NET" --memory 384m --entrypoint anvil "$FOUNDRY" \
  --host 0.0.0.0 --chain-id 31337 --block-time 1 >/dev/null
for _ in $(seq 1 90); do docker exec "$P-mysql" mysql -uroot -p"$DB_PW" -e "SELECT 1 FROM information_schema.schemata WHERE schema_name='avalanche_gateway'" 2>/dev/null | grep -q 1 && break; sleep 2; done

echo "[a2] deploy escrow + MockUSDC to anvil (chain guard in Deploy.s.sol; Anvil unlocked account as deployer, no key)"
# The gateway verifies the manifest at startup, so deploy first with the deployer as placeholder roles (as AvalancheApiTest
# does), then hand the roles to the gateway's ephemeral signers once they exist (F3).
docker run --rm --network "$NET" --memory 768m -v "$WINROOT/avalanche-contracts:/work" -w /work   -e DEPLOY_ENVIRONMENT=LOCAL_SIMULATION -e ARBITER="$ANVIL_SENDER" -e AUTH_SIGNER="$ANVIL_SENDER" "$FOUNDRY"   "forge script script/Deploy.s.sol --rpc-url http://$P-anvil:8545 --unlocked --sender $ANVIL_SENDER --broadcast" > /dev/null
[ -f "$ROOT/$MANIFEST_REL" ] || { echo "deployment manifest not written" >&2; exit 4; }
ESCROW="$(grep -o '"escrow"[^,}]*' "$ROOT/$MANIFEST_REL" | grep -o '0x[0-9a-fA-F]\{40\}')"
[ -n "$ESCROW" ] || { echo "no escrow address in the manifest" >&2; exit 4; }
TOKEN="$(grep -o '"token"[^,}]*' "$ROOT/$MANIFEST_REL" | grep -o '0x[0-9a-fA-F]\{40\}')"
[ -n "$TOKEN" ] || { echo "no token address in the manifest" >&2; exit 4; }

echo "[a2] gateway (LOCAL_SIMULATION, ephemeral development signers)"
docker run -d --name "$P-gateway" --network "$NET" --memory 900m -p 127.0.0.1:19194:9194   -v "$WINROOT:/src" -w /src   -e AVALANCHE_ENVIRONMENT=LOCAL_SIMULATION -e AVALANCHE_RPC_PRIMARY_URL="http://$P-anvil:8545" -e AVALANCHE_RPC_MIN_GAP_MS=0   -e AVALANCHE_SIGNERS_ENABLED=true -e AVALANCHE_TX_ENABLED=true -e AVALANCHE_TX_RECONCILE_INTERVAL_MS=500   -e AVALANCHE_DB_URL="jdbc:mysql://$P-mysql:3306/avalanche_gateway" -e AVALANCHE_DB_USERNAME=root -e AVALANCHE_DB_PASSWORD="$DB_PW"   -e AVALANCHE_DEPLOYMENT_MANIFEST="/src/$MANIFEST_REL" -e AVALANCHE_INTERNAL_API_KEY="$GW_KEY"   maven:3.9-eclipse-temurin-21 java -Xmx512m -jar "/src/$GW_JAR" >/dev/null
SIGNERS=""
for _ in $(seq 1 120); do
  SIGNERS="$(docker logs "$P-gateway" 2>&1 | grep -o 'never persisted): {[^}]*}' | head -1 || true)"
  docker logs "$P-gateway" 2>&1 | grep -q "Started .*Application" && [ -n "$SIGNERS" ] && break
  if ! docker ps --format '{{.Names}}' | grep -q "^$P-gateway$"; then echo "gateway exited" >&2; exit 3; fi
  sleep 2
done
ARBITER="$(echo "$SIGNERS" | grep -o 'ARBITER=0x[0-9a-fA-F]\{40\}' | cut -d= -f2)"
AUTH="$(echo "$SIGNERS" | grep -o 'AUTHORIZATION=0x[0-9a-fA-F]\{40\}' | cut -d= -f2)"
[ -n "$ARBITER" ] && [ -n "$AUTH" ] || { echo "could not read the gateway signer addresses" >&2; exit 3; }
echo "[a2] signers: arbiter $ARBITER, authorization $AUTH"
cast() { docker exec "$P-anvil" cast "$@" --rpc-url http://localhost:8545; }
for addr in $(echo "$SIGNERS" | grep -o '0x[0-9a-fA-F]\{40\}'); do cast rpc anvil_setBalance "$addr" 0x56BC75E2D63100000 >/dev/null; done
AUTH_ROLE="$(docker exec "$P-anvil" cast keccak AUTH_SIGNER_ROLE)"
cast send --unlocked --from "$ANVIL_SENDER" "$ESCROW" "grantRole(bytes32,address)" "$AUTH_ROLE" "$AUTH" >/dev/null
cast send --unlocked --from "$ANVIL_SENDER" "$ESCROW" "setDefaultArbiter(address)" "$ARBITER" >/dev/null
echo "[a2] roles handed to the ephemeral signers (F3)"

echo "[a2] marketplace (Avalanche rail enabled, settlement notifications on, payout adapter with the LOCAL mock partner)"
docker run -d --name "$P-marketplace" --network "$NET" --memory 1200m -p 127.0.0.1:19191:9191 \
  -v "$WINROOT/marketplace-backend/target:/app:ro" \
  -e SPRING_PROFILES_ACTIVE=dev -e DB_URL="jdbc:mysql://$P-mysql:3306/marketplace?useUnicode=true&characterEncoding=UTF-8" \
  -e DB_USERNAME=root -e DB_PASSWORD="$DB_PW" -e REDIS_HOST="$P-redis" -e REDIS_PORT=6379 -e JWT_SECRET="$JWT" \
  -e MISA_PLATFORM_ACCOUNT_PASSWORD="$MISA_PW" -e DEMO_CLIENT_PASSWORD="$DEMO_C" -e DEMO_FREELANCER_PASSWORD="$DEMO_F" -e DEMO_ADMIN_PASSWORD="$DEMO_A" \
  -e AVALANCHE_GATEWAY_ENABLED=true -e AVALANCHE_GATEWAY_BASE_URL="http://$P-gateway:9194" \
  -e AVALANCHE_GATEWAY_INTERNAL_API_KEY="$GW_KEY" -e AVALANCHE_GATEWAY_SETTLEMENT_NOTIFICATIONS=true \
  -e AVALANCHE_PAYOUT_ENABLED=true -e AVALANCHE_PAYOUT_PARTNER=local-mock -e AVALANCHE_PAYOUT_LOCAL_MOCK_TOKEN_ADDRESS="$TOKEN" \
  -e AVALANCHE_PAYOUT_LOCAL_MOCK_DEPOSIT_ADDRESS=0x00000000000000000000000000000000000c0ffe \
  maven:3.9-eclipse-temurin-17 java -Xmx768m -jar /app/backend-0.1.0.jar >/dev/null
for _ in $(seq 1 150); do
  docker logs "$P-marketplace" 2>&1 | grep -q "Started .*Application" && break
  if ! docker ps --format '{{.Names}}' | grep -q "^$P-marketplace$"; then echo "marketplace exited" >&2; exit 5; fi
  sleep 2
done
docker logs "$P-marketplace" 2>&1 | grep -q "Started .*Application" || { echo "marketplace did not start" >&2; exit 5; }

echo "[a2] drive the Avalanche rail end to end"
MARKET=http://127.0.0.1:19191 GATEWAY=http://127.0.0.1:19194 GATEWAY_KEY="$GW_KEY" ANVIL="$P-anvil" OUT="$OUT" \
  ADMIN_EMAIL=admin.e2e@example.test ADMIN_PASSWORD="$DEMO_A" \
  node "$WINHERE/drive.mjs"
echo "[a2] evidence: $OUT"
