#!/usr/bin/env bash
# Run Foundry 1.8.7 (pinned Docker image) against the solidity-foundry prototype. Usage: scripts/forge.sh "<shell command>"
# Example: scripts/forge.sh "forge test -vv"
set -euo pipefail
HERE="$(cd "$(dirname "$0")/../prototypes/solidity-foundry" && pwd -W 2>/dev/null || pwd)"
MSYS_NO_PATHCONV=1 docker run --rm ${DOCKER_EXTRA:-} -v "$HERE:/work" -w /work ghcr.io/foundry-rs/foundry:v1.8.7 "$1"
