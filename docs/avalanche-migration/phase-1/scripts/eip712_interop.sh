#!/usr/bin/env bash
# EIP-712 interoperability: web3j (Java) signature vs Foundry `cast` signature over the SAME typed data.
# Precondition: LocalEvmExperimentsTest has run (writes typed data + valueless ephemeral key to the OS temp dir, NOT the repo).
# The key is passed to the container via an env var and never printed.
set -euo pipefail
TMPD="${TEMP:-/tmp}"
HERE="$(cd "$(dirname "$0")/.." && pwd -W 2>/dev/null || pwd)"
KEYFILE="$TMPD/phase1-eip712-key.txt"; TYPED="$TMPD/phase1-eip712-typed.json"
VEC="$HERE/evidence/eip712-java-vector.json"
PK="$(cat "$KEYFILE")"
JAVA_SIG="$(python -I -c "import json,sys;print(json.load(open(sys.argv[1]))['signature'])" "$VEC")"
SIGNER="$(python -I -c "import json,sys;print(json.load(open(sys.argv[1]))['signer'])" "$VEC")"
CAST_SIG="$(MSYS_NO_PATHCONV=1 docker run --rm -e PK="$PK" -v "$(cygpath -w "$TYPED"):/typed.json:ro" ghcr.io/foundry-rs/foundry:v1.8.7 'cast wallet sign --private-key "$PK" --data --from-file /typed.json')"
echo "java_sig = $JAVA_SIG"
echo "cast_sig = $CAST_SIG"
[ "$JAVA_SIG" = "$CAST_SIG" ] && echo "IDENTICAL signatures (both RFC6979 deterministic, low-s)" || echo "DIFFERENT signatures"
VERIFY="$(MSYS_NO_PATHCONV=1 docker run --rm -v "$(cygpath -w "$TYPED"):/typed.json:ro" ghcr.io/foundry-rs/foundry:v1.8.7 "cast wallet verify --address $SIGNER --data --from-file /typed.json $JAVA_SIG" 2>&1 | tail -1)"
echo "cast verifies java signature: $VERIFY"
DIGEST="$(MSYS_NO_PATHCONV=1 docker run --rm -v "$(cygpath -w "$TYPED"):/typed.json:ro" ghcr.io/foundry-rs/foundry:v1.8.7 'cast hash-typed-data --from-file /typed.json 2>/dev/null || cast hash-typed-data "$(cat /typed.json)"' 2>&1 | tail -1)" || true
echo "cast digest = $DIGEST"
echo "java digest = $(python -I -c "import json,sys;print(json.load(open(sys.argv[1]))['digest'])" "$VEC")"
