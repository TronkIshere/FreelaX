#!/usr/bin/env bash
# Stops the stack started by start-dev.sh, removes its volumes, the local deployment manifest it wrote and its throwaway env file.
set -euo pipefail
export MSYS_NO_PATHCONV=1
HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$HERE/../../../.." && pwd)"
win() { cygpath -m "$1" 2>/dev/null || echo "$1"; }
P="${STACK_PROJECT:-fxav}"
ENVF="$ROOT/.avalanche-dev/env"
[ -f "$ENVF" ] || { echo "no running dev stack found ($ENVF missing)" >&2; exit 1; }
docker compose -p "$P" -f "$(win "$ROOT/docker-compose.yml")" -f "$(win "$HERE/docker-compose.anvil.yml")" --env-file "$(win "$ENVF")" \
  --profile avalanche --profile deploy down -v
M="$ROOT/avalanche-contracts/deployments/LOCAL_SIMULATION.json"
rm -f "$M"; [ -f "$M.fxav-backup" ] && mv "$M.fxav-backup" "$M"
rm -f "$ENVF"
echo "[fxav] dev stack stopped and removed"
