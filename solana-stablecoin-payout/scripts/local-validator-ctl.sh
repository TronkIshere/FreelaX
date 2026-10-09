#!/bin/bash
# Local-only helper for outage E2E: stop|start the unified demo validator on 9123.
set -e
cd "$(dirname "$0")/../.."
L=solana-stablecoin-payout/target/unified-ledger-20261009
running() { pgrep -f '^solana-test-validator' >/dev/null; }
case "$1" in
  stop)
    # The test validator does not act on SIGINT promptly; TERM then wait for a real exit.
    for p in $(pgrep -f '^solana-test-validator'); do kill -TERM "$p"; done
    for i in $(seq 1 60); do running || exit 0; sleep 1; done
    for p in $(pgrep -f '^solana-test-validator'); do kill -KILL "$p"; done
    for i in $(seq 1 20); do running || exit 0; sleep 1; done
    exit 1 ;;
  start)
    for i in $(seq 1 30); do running || break; sleep 1; done
    running && { echo "validator still running" >&2; exit 1; }
    for i in $(seq 1 30); do ss -ltn 2>/dev/null | grep -q ':9123 ' || break; sleep 1; done
    rm -f "$L/ledger.lock"
    nohup setsid solana-test-validator --ledger "$L" --rpc-port 9123 --faucet-port 9125 \
      --bind-address 127.0.0.1 --quiet > solana-stablecoin-payout/target/unified-validator.log 2>&1 < /dev/null &
    pid=$!
    for i in $(seq 1 150); do
      kill -0 "$pid" 2>/dev/null || { echo "validator exited during startup" >&2; exit 1; }
      curl -fsS -m 2 -H 'Content-Type: application/json' \
        --data '{"jsonrpc":"2.0","id":1,"method":"getHealth"}' http://127.0.0.1:9123 >/dev/null 2>&1 && break
      sleep 2
    done
    # Healthy is not enough: wait until new blocks are produced, or sent transactions drop.
    first=$(solana slot --url http://127.0.0.1:9123 --commitment processed 2>/dev/null || echo 0)
    for i in $(seq 1 90); do
      sleep 2
      now=$(solana slot --url http://127.0.0.1:9123 --commitment processed 2>/dev/null || echo 0)
      [ "$now" -gt $((first + 20)) ] && exit 0
    done
    exit 1 ;;
esac
