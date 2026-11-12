#!/usr/bin/env bash
# Starts the full LOCAL_SIMULATION Avalanche stack with Docker Compose and LEAVES IT RUNNING for development (owner decision
# 2026-11-12: Avalanche is the default rail and the marketplace rail is on). It is run.sh in KEEP_STACK mode: images built by
# compose, Anvil (chain 31337), escrow and MockUSDC deployed with forge, the role hand-over to the gateway's ephemeral signers,
# then payment, MISA, marketplace and the frontend. Throwaway secrets only; the owner's .env is never read. No Fuji, no Mainnet.
# Every start is a fresh stack (previous data of project fxav is removed). Optional drivers run once the stack is up.
# Usage: bash docs/avalanche-migration/phase-2/compose-avalanche/start-dev.sh [driver.mjs ...]     Stop: bash .../stop-dev.sh
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$HERE/../../../.." && pwd)"
mkdir -p "$ROOT/.avalanche-dev/evidence"
KEEP_STACK=1 exec bash "$HERE/run.sh" "$ROOT/.avalanche-dev/evidence" "$@"
