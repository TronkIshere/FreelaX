#!/usr/bin/env sh
# Phase 1 E2 preparation: validate the six read-only queries of ../db-rail-evidence-request.md against an EMPTY, throwaway,
# local MySQL 8.4 whose schema is built from this repository's own Flyway migrations (marketplace V1..Vn, payment V1..Vn).
#
# What this proves: the queries parse, every column exists in the CURRENT schema, they run under a SELECT-only account in a
# READ ONLY session, and that session refuses writes. What it does NOT prove: anything about any real database. The counts
# are from an empty local schema and are NOT E2 evidence (E2 stays BLOCKED on the DB operator; R2: development DB only).
#
# Safety: starts its own container with no published port, never reads .env, connects to nothing else, removes the container.
# Usage (from the repository root, Docker running): sh docs/avalanche-migration/phase-1/scripts/db_query_validate.sh [out.txt]
set -eu
ROOT=$(cd "$(dirname "$0")/../../../.." && pwd)
REQ="$ROOT/docs/avalanche-migration/phase-1/db-rail-evidence-request.md"
OUT=${1:-/dev/stdout}
NAME="freelax-e2-validate-$$"
PW="local-throwaway-$$"   # LOCAL container only, discarded with it; not a credential for anything else
export MSYS_NO_PATHCONV=1

cleanup() { docker rm -f "$NAME" >/dev/null 2>&1 || true; }
trap cleanup EXIT INT TERM

docker run -d --name "$NAME" -e MYSQL_ROOT_PASSWORD="$PW" mysql:8.4 >/dev/null
for _ in $(seq 1 90); do
  docker exec "$NAME" mysql -uroot -p"$PW" -e "SELECT 1" >/dev/null 2>&1 && break
  sleep 2
done
root() { docker exec -i "$NAME" mysql -uroot -p"$PW" "$@" 2>/dev/null; }

root -e "CREATE DATABASE marketplace CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
         CREATE DATABASE payment CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
apply() { # $1 database, $2 migration directory; versions applied in numeric order, as Flyway does
  for f in $(ls "$2" | sort -t V -k2 -n); do
    root "$1" < "$2/$f"
    echo "applied $1 $f"
  done
}
MIG_M=$(apply marketplace "$ROOT/marketplace-backend/src/main/resources/db/migration")
MIG_P=$(apply payment "$ROOT/payment-backend/src/main/resources/db/migration")

# Dedicated SELECT-only account, as the operator checklist requires.
root -e "CREATE USER 'e2_readonly'@'%' IDENTIFIED BY '$PW-ro';
         GRANT SELECT ON marketplace.* TO 'e2_readonly'@'%'; GRANT SELECT ON payment.* TO 'e2_readonly'@'%';"
# -vvv echoes each statement with its result ("Empty set" on the empty schema), so the output shows that every query ran;
# errors (ERROR nnnn) are kept in the output. An empty filter result is not a failure.
ro() { docker exec -i "$NAME" mysql -ue2_readonly -p"$PW-ro" --table -vvv "$@" 2>&1 | grep -v "Using a password" || true; }

# The queries exactly as written in the request document (the ```sql block), split per database comment.
SQL=$(awk '/^```sql/{f=1;next} /^```/{if(f){exit}} f' "$REQ")
Q_M=$(printf '%s\n' "$SQL" | awk '/^-- database: marketplace/{f=1;next} /^-- database:/{f=0} f')
Q_P=$(printf '%s\n' "$SQL" | awk '/^-- database: payment/{f=1;next} /^-- database:/{f=0} f')
COUNT=$(printf '%s\n%s\n' "$Q_M" "$Q_P" | grep -c ';' || true)

{
  echo "label: LOCAL-SIM query validation (empty throwaway schema; NOT E2 evidence)"
  echo "generated_at_utc: $(date -u +%Y-%m-%dT%H:%M:%SZ)"
  echo "mysql: $(root -N -e 'SELECT VERSION()')"
  echo "queries_found_in_request_document: $COUNT"
  echo "$MIG_M"; echo "$MIG_P"
  echo "--- marketplace (SELECT-only account, READ ONLY session)"
  printf 'SET SESSION TRANSACTION READ ONLY;\n%s\n' "$Q_M" | ro marketplace
  echo "--- payment (SELECT-only account, READ ONLY session)"
  printf 'SET SESSION TRANSACTION READ ONLY;\n%s\n' "$Q_P" | ro payment
  echo "--- write refusal checks (expected: errors)"
  printf 'SET SESSION TRANSACTION READ ONLY;\nDELETE FROM jobs WHERE 1=0;\n' | ro marketplace || true
  printf 'START TRANSACTION READ ONLY;\nCREATE TABLE marketplace.e2_probe (x INT);\n' | ro marketplace || true
  echo "--- end"
} > "$OUT"
