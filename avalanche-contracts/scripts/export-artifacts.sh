#!/usr/bin/env bash
# Exports the ABI (committed, used by the gateway for event and error decoding) and the creation bytecode (test fixture for the
# gateway's integration tests) from the compiled contracts, using the pinned Foundry image. Re-run after any contract change.
set -euo pipefail
cd "$(dirname "$0")/.."
GATEWAY="../avalanche-integration/src"
mkdir -p abi "$GATEWAY/main/resources/abi" "$GATEWAY/test/resources/contracts"
./scripts/forge.sh "forge build >/dev/null 2>&1 && forge inspect FreelaXEscrow abi --json > abi/FreelaXEscrow.abi.json && forge inspect FreelaXEscrow bytecode > abi/FreelaXEscrow.bin && forge inspect MockUSDC abi --json > abi/MockUSDC.abi.json && forge inspect MockUSDC bytecode > abi/MockUSDC.bin"
cp abi/FreelaXEscrow.abi.json "$GATEWAY/main/resources/abi/FreelaXEscrow.abi.json"
cp abi/FreelaXEscrow.bin "$GATEWAY/test/resources/contracts/FreelaXEscrow.bin"
cp abi/MockUSDC.bin "$GATEWAY/test/resources/contracts/MockUSDC.bin"
echo "exported: $(wc -c < abi/FreelaXEscrow.abi.json) bytes ABI, $(wc -c < abi/FreelaXEscrow.bin) chars escrow bytecode"
