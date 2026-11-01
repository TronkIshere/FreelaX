#!/usr/bin/env bash
# One-command LOCAL_SIMULATION deployment against an Anvil node using Anvil's UNLOCKED account (no key is passed or stored).
# Usage: ARBITER=<addr> AUTH_SIGNER=<addr> scripts/deploy-local.sh http://host.docker.internal:8545
set -euo pipefail
RPC="${1:?usage: ARBITER=<addr> AUTH_SIGNER=<addr> scripts/deploy-local.sh <anvil rpc url>}"
: "${ARBITER:?}" "${AUTH_SIGNER:?}"
cd "$(dirname "$0")/.."
DOCKER_EXTRA="-e DEPLOY_ENVIRONMENT=LOCAL_SIMULATION -e ARBITER=$ARBITER -e AUTH_SIGNER=$AUTH_SIGNER" \
  ./scripts/forge.sh "forge script script/Deploy.s.sol --rpc-url $RPC --unlocked --sender 0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266 --broadcast"
echo "manifest: deployments/LOCAL_SIMULATION.json"
