#!/usr/bin/env bash
# Runs Foundry 1.8.7 from the pinned Docker image against this project. Usage: scripts/forge.sh "forge test -vv"
# Fork tests (optional, read-only RPC to the public Fuji endpoint) need FUJI_RPC passed through: DOCKER_EXTRA="-e FUJI_RPC=..."
set -euo pipefail
HERE="$(cd "$(dirname "$0")/.." && pwd -W 2>/dev/null || pwd)"
MSYS_NO_PATHCONV=1 docker run --rm ${DOCKER_EXTRA:-} -v "$HERE:/work" -w /work ghcr.io/foundry-rs/foundry:v1.8.7 "$1"
