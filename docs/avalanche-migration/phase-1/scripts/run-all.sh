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
