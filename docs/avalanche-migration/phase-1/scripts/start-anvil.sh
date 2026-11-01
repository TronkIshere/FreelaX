#!/usr/bin/env bash
# Starts two LOCAL anvil instances (pinned Foundry 1.8.7 image): :8545 plain chain 31337, :8546 fork of Fuji (chain 43113).
set -euo pipefail
IMG=ghcr.io/foundry-rs/foundry:v1.8.7
docker rm -f freelax-anvil freelax-anvil-fork >/dev/null 2>&1 || true
docker run -d --name freelax-anvil -p 8545:8545 $IMG "anvil --host 0.0.0.0 --chain-id 31337 --hardfork cancun" >/dev/null
docker run -d --name freelax-anvil-fork -p 8546:8545 $IMG "anvil --host 0.0.0.0 --fork-url ${FUJI_RPC:-https://api.avax-test.network/ext/bc/C/rpc} --hardfork cancun" >/dev/null
sleep 6
