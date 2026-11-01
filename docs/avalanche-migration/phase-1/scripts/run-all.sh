#!/usr/bin/env bash
# Reproduces every Phase 1 experiment. Requirements: docker, curl, python 3, node >= 22, JDK 21 + Maven 3.9 (set JAVA_HOME to JDK 21; Maven wrapper path in $MVN).
# Writes evidence/*. No private keys, no mainnet writes, no Fuji writes (only one rejected unfunded submit).
set -euo pipefail
cd "$(dirname "$0")/.."
FUJI=https://api.avax-test.network/ext/bc/C/rpc
python -I scripts/fuji_probe.py $FUJI official
python -I scripts/fuji_probe.py https://avalanche-fuji-c-chain-rpc.publicnode.com publicnode
node prototypes/ws-observer/observe.mjs 90 wss://api.avax-test.network/ext/bc/C/ws $FUJI evidence/ws-observe-official.json
node prototypes/ws-observer/finality_probe.mjs 75 wss://api.avax-test.network/ext/bc/C/ws $FUJI evidence/finality-probe-official.json
node prototypes/ws-observer/timestamp_probe.mjs $FUJI evidence/timestamp-probe-official.json
# Solidity (first time: forge install --no-git OpenZeppelin/openzeppelin-contracts@v5.7.0 foundry-rs/forge-std inside prototypes/solidity-foundry)
bash scripts/forge.sh "forge test --no-match-contract UsdcFork --gas-report"
bash scripts/forge.sh "forge test --match-contract UsdcFork --fork-url $FUJI -vv"
# Java
bash scripts/start-anvil.sh
( cd prototypes/java-web3j && "${MVN:-mvn}" -B -ntp test )
bash scripts/eip712_interop.sh

# ---- Part B additions (conservative rates; paced helper rpc_util.mjs; honours Retry-After) ----
export MIN_GAP_MS=1500
node prototypes/ws-observer/provider_probe.mjs evidence/provider-probe-fuji.json
node prototypes/ws-observer/ws_reconnect.mjs 20 15 20 evidence/ws-reconnect-fuji.json
node prototypes/ws-observer/consistency_probe.mjs 30 8 evidence/consistency-probe-fuji.json
ST="${TEMP:-/tmp}/phase1-cursor-state.json"; rm -f "$ST"
node prototypes/ws-observer/cursor_scan.mjs "$ST" evidence/cursor-scan-fuji.json 12 || true     # simulated crash
node prototypes/ws-observer/cursor_scan.mjs "$ST" evidence/cursor-scan-fuji.json                # restart; must report equal=true
rm -f "$ST"
bash scripts/web_matrix.sh                       # Spring Boot web context x {Java 21,17} x {no override, annotations22, bom22}
W3J=4.13.0 PROFILES=none bash scripts/web_matrix.sh
bash scripts/java_matrix.sh                      # full suite on Java21+6.1.0, Java17+4.13.0, Java21+4.13.0 (needs anvil from start-anvil.sh)
python -I scripts/evm_fork_probe.py "$FUJI"
# Blocked until a human funds an ephemeral Fuji wallet: see fuji-funding-runbook.md (LiveExperimentsTest generate/run)
