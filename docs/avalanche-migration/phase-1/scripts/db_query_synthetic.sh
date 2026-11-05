#!/usr/bin/env sh
# Phase 1 E2, LOCAL synthetic-data validation of the six read-only queries of ../db-rail-evidence-request.md.
#
# This is NOT the E2 evidence. E2 is the owner's run of the six queries against the DEVELOPMENT database (R2, 2026-11-05) and stays
# BLOCKED until then. This script proves, on a disposable local MySQL 8.4 built from this repository's own Flyway migrations and
# filled with a small SYNTHETIC fixture whose counts are known in advance, that
#   1. the six queries, extracted unchanged from the request document, return exactly the expected counts;
#   2. they run under a SELECT-only account in a READ ONLY session; writes and DDL are refused;
#   3. they change nothing: CHECKSUM TABLE and row counts of every queried table are identical before and after.
# Safety: its own container, no published port, never reads .env, throwaway password, container removed on exit.
# The fixture is loaded by the root account of that container only, with sql_mode relaxed and foreign keys off (the rows exist only
# to be counted; they are not business-valid records).
# Usage (repository root, Docker running): sh docs/avalanche-migration/phase-1/scripts/db_query_synthetic.sh [out.txt]
set -eu
ROOT=$(cd "$(dirname "$0")/../../../.." && pwd)
REQ="$ROOT/docs/avalanche-migration/phase-1/db-rail-evidence-request.md"
OUT=${1:-/dev/stdout}
NAME="freelax-e2-synthetic-$$"
PW="local-throwaway-$$"
TAB=$(printf '\t')
export MSYS_NO_PATHCONV=1
cleanup() { docker rm -f "$NAME" >/dev/null 2>&1 || true; }
trap cleanup EXIT INT TERM

docker run -d --name "$NAME" -e MYSQL_ROOT_PASSWORD="$PW" mysql:8.4 >/dev/null
for _ in $(seq 1 90); do docker exec "$NAME" mysql -uroot -p"$PW" -e "SELECT 1" >/dev/null 2>&1 && break; sleep 2; done
root() { docker exec -i "$NAME" mysql -uroot -p"$PW" "$@" 2>&1 | grep -v "Using a password" || true; }
rootq() { docker exec -i "$NAME" mysql -uroot -p"$PW" -N -B "$@" 2>/dev/null; }

root -e "CREATE DATABASE marketplace CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci; CREATE DATABASE payment CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;" >/dev/null
apply() { for f in $(ls "$2" | sort -t V -k2 -n); do docker exec -i "$NAME" mysql -uroot -p"$PW" "$1" < "$2/$f" 2>/dev/null; done; ls "$2" | sort -t V -k2 -n | tail -1; }
LAST_M=$(apply marketplace "$ROOT/marketplace-backend/src/main/resources/db/migration")
LAST_P=$(apply payment "$ROOT/payment-backend/src/main/resources/db/migration")

# ---- synthetic fixture (counts known in advance) -----------------------------------------------------------------------------
U="UNHEX(REPLACE(UUID(),'-',''))"
FIXTURE=$(root <<SQL
SET SESSION sql_mode = ''; SET SESSION foreign_key_checks = 0;
USE marketplace;
-- jobs: 3 x (flow version NULL, COMPLETED; 2 with a checkout order), 1 x (2, COMPLETED, with a checkout order), 2 x (2, IN_PROGRESS)
INSERT INTO jobs (id, status, payment_flow_version, checkout_order_id) VALUES
 ($U,'COMPLETED',NULL,$U), ($U,'COMPLETED',NULL,$U), ($U,'COMPLETED',NULL,NULL),
 ($U,'COMPLETED',2,$U), ($U,'IN_PROGRESS',2,NULL), ($U,'IN_PROGRESS',2,NULL);
-- work_contracts: 2 x (NULL, ACTIVE), 1 x (SOLANA_ESCROW, COMPLETED), 1 x (PARTNER_ESCROW_MOCK, ACTIVE)
INSERT INTO work_contracts (id, job_id, status, payment_rail) VALUES
 ($U,$U,'ACTIVE',NULL), ($U,$U,'ACTIVE',NULL), ($U,$U,'COMPLETED','SOLANA_ESCROW'), ($U,$U,'ACTIVE','PARTNER_ESCROW_MOCK');
-- freelancer_payout_records: 1 x fully confirmed and contract driven, 2 x not started and not contract driven
INSERT INTO freelancer_payout_records (id, job_id, on_ramp_status, off_ramp_status, client_payment_status, on_chain_off_ramp_status, contract_settlement_id) VALUES
 ($U,$U,'CONFIRMED','COMPLETED','CONFIRMED','CONFIRMED',$U),
 ($U,$U,'NOT_STARTED','NOT_STARTED','NOT_STARTED','NOT_STARTED',NULL), ($U,$U,'NOT_STARTED','NOT_STARTED','NOT_STARTED','NOT_STARTED',NULL);
USE payment;
-- bofa_checkout_orders: 2 CAPTURED, 1 CREATED, 1 FAILED
INSERT INTO bofa_checkout_orders (id, bofa_order_id, idempotency_key, status) VALUES
 ($U,'synthetic-1','synthetic-key-1','CAPTURED'), ($U,'synthetic-2','synthetic-key-2','CAPTURED'),
 ($U,'synthetic-3','synthetic-key-3','CREATED'), ($U,'synthetic-4','synthetic-key-4','FAILED');
SQL
)
case "$FIXTURE" in *ERROR*) echo "fixture load failed: $FIXTURE" >&2; exit 2;; esac

# dedicated SELECT-only account, as the operator checklist requires
root -e "CREATE USER 'e2_readonly'@'%' IDENTIFIED BY '$PW-ro'; GRANT SELECT ON marketplace.* TO 'e2_readonly'@'%'; GRANT SELECT ON payment.* TO 'e2_readonly'@'%';" >/dev/null
ro() { docker exec -i "$NAME" mysql -ue2_readonly -p"$PW-ro" -N -B "$@" 2>&1 | grep -v "Using a password" || true; }

snapshot() {
  for t in marketplace.jobs marketplace.work_contracts marketplace.freelancer_payout_records payment.bofa_checkout_orders; do
    printf '%s checksum=%s rows=%s\n' "$t" "$(rootq -e "CHECKSUM TABLE $t" | cut -f2)" "$(rootq -e "SELECT COUNT(*) FROM $t")"
  done
}
BEFORE=$(snapshot)

# the six statements, extracted unchanged from the request document and split on ';'
SQL=$(awk '/^```sql/{f=1;next} /^```/{if(f){exit}} f' "$REQ")
split() { awk 'BEGIN{RS=";"} NF{gsub(/\n/," "); print $0 ";"}'; }
Q_M=$(printf '%s\n' "$SQL" | awk '/^-- database: marketplace/{f=1;next} /^-- database:/{f=0} f' | split)
Q_P=$(printf '%s\n' "$SQL" | awk '/^-- database: payment/{f=1;next} /^-- database:/{f=0} f' | split)
N_M=$(printf '%s\n' "$Q_M" | grep -c ';'); N_P=$(printf '%s\n' "$Q_P" | grep -c ';')

FAIL=0
check() { # $1 label, $2 database, $3 statement, $4 expected output
  got=$(printf 'SET SESSION TRANSACTION READ ONLY;\n%s\n' "$3" | ro "$2")
  if [ "$got" = "$4" ]; then v=MATCH; else v=MISMATCH; FAIL=1; fi
  printf '%s %s\n  statement: %s\n  expected:\n%s\n  got:\n%s\n' "$1" "$v" "$3" "$(printf '%s\n' "$4" | sed 's/^/    /')" "$(printf '%s\n' "$got" | sed 's/^/    /')"
}
Q1=$(printf '%s\n' "$Q_M" | sed -n 1p); Q2=$(printf '%s\n' "$Q_M" | sed -n 2p); Q3=$(printf '%s\n' "$Q_M" | sed -n 3p); Q4=$(printf '%s\n' "$Q_M" | sed -n 4p)
Q5=$(printf '%s\n' "$Q_P" | sed -n 1p); Q6=$(printf '%s\n' "$Q_P" | sed -n 2p)

{
  echo "label: LOCAL-SIM synthetic-data validation of the six E2 queries (NOT the E2 evidence; no real or development data)"
  echo "generated_at_utc: $(date -u +%Y-%m-%dT%H:%M:%SZ)"
  echo "mysql: $(rootq -e 'SELECT VERSION()')"
  echo "latest migration applied: marketplace $LAST_M, payment $LAST_P"
  echo "statements extracted from the request document: marketplace $N_M, payment $N_P (expected 4 and 2)"
  [ "$N_M" = 4 ] && [ "$N_P" = 2 ] || FAIL=1
  check Q1 marketplace "$Q1" "NULL${TAB}COMPLETED${TAB}3
2${TAB}COMPLETED${TAB}1
2${TAB}IN_PROGRESS${TAB}2"
  check Q2 marketplace "$Q2" "NULL${TAB}ACTIVE${TAB}2
PARTNER_ESCROW_MOCK${TAB}ACTIVE${TAB}1
SOLANA_ESCROW${TAB}COMPLETED${TAB}1"
  check Q3 marketplace "$Q3" "CONFIRMED${TAB}COMPLETED${TAB}CONFIRMED${TAB}CONFIRMED${TAB}1${TAB}1
NOT_STARTED${TAB}NOT_STARTED${TAB}NOT_STARTED${TAB}NOT_STARTED${TAB}0${TAB}2"
  check Q4 marketplace "$Q4" "COMPLETED${TAB}3"
  check Q5 payment "$Q5" "CAPTURED${TAB}2
CREATED${TAB}1
FAILED${TAB}1"
  check Q6 payment "$Q6" "1"
  echo "--- write and DDL refusal under the SELECT-only account (each must print an ERROR)"
  w1=$(printf 'SET SESSION TRANSACTION READ ONLY;\nDELETE FROM jobs WHERE 1=0;\n' | ro marketplace)
  w2=$(printf 'SET SESSION TRANSACTION READ ONLY;\nUPDATE work_contracts SET status = status WHERE 1=0;\n' | ro marketplace)
  w3=$(printf 'CREATE TABLE marketplace.e2_probe (x INT);\n' | ro marketplace)
  for w in "$w1" "$w2" "$w3"; do case "$w" in *ERROR*) echo "refused: $(printf '%s' "$w" | head -1 | cut -c1-120)";; *) echo "NOT REFUSED"; FAIL=1;; esac; done
  echo "--- unchanged state (CHECKSUM TABLE and row counts before and after all queries and refused writes)"
  AFTER=$(snapshot)
  printf 'before:\n%s\nafter:\n%s\n' "$BEFORE" "$AFTER"
  if [ "$BEFORE" = "$AFTER" ]; then echo "state: UNCHANGED"; else echo "state: CHANGED"; FAIL=1; fi
  if [ "$FAIL" = 0 ]; then echo "result: PASS"; else echo "result: FAIL"; fi
} > "$OUT"
[ "$FAIL" = 0 ]
