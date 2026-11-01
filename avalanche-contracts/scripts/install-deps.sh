#!/usr/bin/env bash
# Installs the pinned Solidity dependencies into lib/ (ignored by git). Versions are the ones validated in Phase 1:
# OpenZeppelin Contracts 5.7.0. Prefer copying the already-vendored Phase 1 copy; otherwise fetch the pinned tag.
set -euo pipefail
cd "$(dirname "$0")/.."
PROTO="../docs/avalanche-migration/phase-1/prototypes/solidity-foundry/lib"
if [ -d "$PROTO/openzeppelin-contracts" ] && [ -d "$PROTO/forge-std" ]; then
  rm -rf lib && cp -r "$PROTO" lib
else
  mkdir -p lib
  git clone --depth 1 --branch v5.7.0 https://github.com/OpenZeppelin/openzeppelin-contracts.git lib/openzeppelin-contracts
  git clone --depth 1 https://github.com/foundry-rs/forge-std.git lib/forge-std
fi
