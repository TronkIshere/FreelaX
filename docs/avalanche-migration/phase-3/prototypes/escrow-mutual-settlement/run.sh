#!/usr/bin/env bash
# R12 / F14 lab runner (LOCAL_SIMULATION only; no RPC, no key, no deployment).
# 1. Proves the variant differs from the baseline only by the documented addition (diff written to evidence).
# 2. Copies the baseline Foundry suites (Base, Core, Lifecycle, Invariants) into test/baseline/ (git-ignored) with their
#    imports pointed at the variant, so every baseline behaviour is re-checked against it. Deploy.t.sol (script, chain
#    guard) and UsdcFork.t.sol (Fuji fork) are not copied: they test the deployment path, which the variant does not have.
# 3. Runs everything in the pinned Foundry image.
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
repo="$(cd "$here/../../../../.." && pwd)"
base="$repo/avalanche-contracts"
out="${1:-$here/LOCAL_SIMULATION-mutual-settlement-$(date -u +%Y%m%d).txt}"

rm -rf "$here/test/baseline" && mkdir -p "$here/test/baseline"
for f in Base.t.sol FreelaXEscrowCore.t.sol FreelaXEscrowLifecycle.t.sol Invariants.t.sol; do
  sed -e 's#import {FreelaXEscrow} from "../src/FreelaXEscrow.sol";#import {FreelaXEscrowMutualSettlement as FreelaXEscrow} from "../../src/FreelaXEscrowMutualSettlement.sol";#' \
      -e 's#"../src/interfaces/#"@freelax/interfaces/#' \
      -e 's#"../src/mocks/#"@freelax/mocks/#' \
      "$base/test/$f" > "$here/test/baseline/$f"
  if grep -q '"\.\./src/FreelaXEscrow.sol"' "$here/test/baseline/$f"; then echo "rewrite failed for $f" >&2; exit 1; fi
done

{
  echo "# R12 mutual-settlement lab, LOCAL_SIMULATION, $(date -u +%Y-%m-%dT%H:%M:%SZ)"
  echo "# baseline avalanche-contracts/src/FreelaXEscrow.sol sha256: $(sha256sum "$base/src/FreelaXEscrow.sol" | cut -d' ' -f1)"
  echo "## diff baseline -> variant (only the documented addition is expected)"
  diff -u "$base/src/FreelaXEscrow.sol" "$here/src/FreelaXEscrowMutualSettlement.sol" || true
  echo "## forge test"
} > "$out"

MSYS_NO_PATHCONV=1 docker run --rm -v "$repo:/repo" -w /repo/docs/avalanche-migration/phase-3/prototypes/escrow-mutual-settlement \
  ghcr.io/foundry-rs/foundry:v1.8.7 "forge test -vv" 2>&1 | tee -a "$out"
