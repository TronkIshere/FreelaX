#!/bin/sh
# Phase 1 E1: the HUMAN OPERATOR runs this to pre-sign the experiment transactions with a signer THEY control.
# Nothing here holds, reads or stores a private key: the signer is chosen through SIGNER_FLAGS and handled by `cast` itself.
# The output is plan.jsonl (signed transactions only: public data once broadcast). Broadcast it with broadcast_signed.mjs.
#
# Required environment:
#   SIGNER_FLAGS   how cast reaches the human-held signer, e.g. "--ledger" or "--keystore <path>"  (owner decision C1;
#                  "--private-key" is refused so no key ever appears on a command line or in shell history)
#   SENDER         the signer's address (public)
#   NONCE          the sender's current nonce (read it with: cast nonce $SENDER --rpc-url $RPC)
#   CHAIN_ID       31337 (LOCAL_SIMULATION) or 43113 (AVALANCHE_FUJI); anything else is refused
#   MAX_FEE        maxFeePerGas in wei, PRIORITY  maxPriorityFeePerGas in wei (read current values; never hard-coded)
#   T0             unix seconds a few minutes in the future: E7 deadline samples start when the chain head reaches T0
# Optional: RPC (read-only, used by cast for estimation fallbacks), CAST (default: cast), PLAN (default: plan.jsonl),
#           BIN (default: ../java-web3j/src/test/resources/Phase1Lab.bin)
set -eu
CAST="${CAST:-cast}"
PLAN="${PLAN:-plan.jsonl}"
BIN="${BIN:-$(dirname "$0")/../java-web3j/src/test/resources/Phase1Lab.bin}"
: "${SIGNER_FLAGS:?set SIGNER_FLAGS (owner-approved human-held signer, e.g. --ledger)}" "${SENDER:?}" "${NONCE:?}" "${CHAIN_ID:?}" "${MAX_FEE:?}" "${PRIORITY:?}" "${T0:?}"
case "$SIGNER_FLAGS" in *private-key*|*mnemonic*) echo "REFUSED: SIGNER_FLAGS must not carry a key or mnemonic" >&2; exit 2;; esac
case "$CHAIN_ID" in 31337|43113) ;; *) echo "REFUSED: chain id $CHAIN_ID is not 31337 or 43113 (Mainnet is out of scope)" >&2; exit 2;; esac
[ -f "$PLAN" ] && { echo "REFUSED: $PLAN exists; move it away first" >&2; exit 2; }

RPC_FLAG=""; [ -n "${RPC:-}" ] && RPC_FLAG="--rpc-url $RPC"
COMMON="--chain $CHAIN_ID $SIGNER_FLAGS $RPC_FLAG"
fee() { # fee <percent> -> "--gas-price X --priority-gas-price Y"
  echo "--gas-price $(( MAX_FEE * $1 / 100 )) --priority-gas-price $(( PRIORITY * $1 / 100 > 0 ? PRIORITY * $1 / 100 : 1 ))"
}
line() { printf '%s\n' "$1" >> "$PLAN"; }
n=$NONCE
LAB=$($CAST compute-address "$SENDER" --nonce "$n" | awk '{print $NF}')
OPERATOR_ROLE=$($CAST keccak "OPERATOR_ROLE")

# E1 deploy of the disposable lab contract (admin = the human sender); timing to receipt and to finalized
raw=$($CAST mktx $COMMON --nonce $n --gas-limit 3000000 $(fee 100) --create "$(cat "$BIN")" "constructor(address)" "$SENDER")
line "{\"id\":\"E1-deploy\",\"raw\":\"$raw\",\"wait\":\"finalized\"}"; n=$((n+1))
raw=$($CAST mktx $COMMON --nonce $n --gas-limit 200000 $(fee 100) "$LAB" "grantRole(bytes32,address)" "$OPERATOR_ROLE" "$SENDER")
line "{\"id\":\"setup-grant-operator\",\"raw\":\"$raw\",\"wait\":\"receipt\"}"; n=$((n+1))
# E2 success timing (with the #16 eth_getLogs probe until the receipt); E4 duplicate submission of the same signed bytes (immediately and after mining)
raw=$($CAST mktx $COMMON --nonce $n --gas-limit 200000 $(fee 100) "$LAB" "setValue(uint256)" 7)
line "{\"id\":\"E2-success+E4-duplicate\",\"raw\":\"$raw\",\"wait\":\"finalized\",\"resendImmediately\":true,\"resendAfterReceipt\":true,\"probeLogs\":true}"; n=$((n+1))
# E3 revert: accepted at submission, executes with receipt status 0 (explicit gas limit, no estimation)
raw=$($CAST mktx $COMMON --nonce $n --gas-limit 100000 $(fee 100) "$LAB" "boom(uint256)" 42)
line "{\"id\":\"E3-revert\",\"raw\":\"$raw\",\"wait\":\"receipt\"}"; n=$((n+1))
# E5 nonce gap (nonce n+1 first) and a replacement ladder for that nonce; the first accepted bump ends the ladder; then the filler n
gap=$((n+1))
raw=$($CAST mktx $COMMON --nonce $gap --gas-limit 200000 $(fee 100) "$LAB" "setValue(uint256)" 11)
line "{\"id\":\"E5-gap\",\"raw\":\"$raw\",\"wait\":\"none\"}"
for pct in 100 105 110 115 125 150 200; do
  raw=$($CAST mktx $COMMON --nonce $gap --gas-limit 200000 $(fee $pct) "$LAB" "setValue(uint256)" $((100 + pct)))
  line "{\"id\":\"E5-bump-$pct\",\"raw\":\"$raw\",\"wait\":\"none\",\"group\":\"ladder\"}"
done
raw=$($CAST mktx $COMMON --nonce $n --gas-limit 200000 $(fee 100) "$LAB" "setValue(uint256)" 10)
line "{\"id\":\"E5-filler\",\"raw\":\"$raw\",\"wait\":\"finalized\"}"; n=$((gap+1))
# E7 near-deadline inclusion: sample i is broadcast once the head reaches T_i = T0 + SPACING*i, with deadline T_i + k
SPACING="${SPACING:-60}"
i=0
for k in 0 1 2 3 5 8; do
  ti=$((T0 + SPACING * i)); d=$((ti + k))
  raw=$($CAST mktx $COMMON --nonce $n --gas-limit 100000 $(fee 100) "$LAB" "actUntil(uint256)" $d)
  line "{\"id\":\"E7-k$k\",\"raw\":\"$raw\",\"wait\":\"receipt\",\"notBeforeHeadTs\":$ti,\"deadline\":$d}"
  n=$((n+1)); i=$((i+1))
done
echo "wrote $PLAN (lab contract will be $LAB); next: node broadcast_signed.mjs <env> <rpc> $PLAN <evidence.json>"
