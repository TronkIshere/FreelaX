#!/usr/bin/env bash
# Part B tasks A+B: run the FULL 14-test java-web3j prototype (anvil + Fuji read-only + Fuji fork) on {Java, web3j} combinations.
# Requires: scripts/start-anvil.sh already running (LOCAL_SIMULATION :8545, local Fuji fork :8546). Env: MVN, JDK21, JDK17.
# Output: evidence/java-matrix/<tag>/ (JSON evidence + surefire log). No secrets; temp key files written by the EIP-712 test are deleted.
set -uo pipefail
cd "$(dirname "$0")/../prototypes/java-web3j"
MVN="${MVN:-mvn}"
JDK21="${JDK21:-C:\\Program Files\\Java\\jdk-21}"
JDK17="${JDK17:?set JDK17 to a JDK 17 home directory}"
COMBOS="${COMBOS:-21:6.1.0 17:4.13.0 21:4.13.0}"
for COMBO in $COMBOS; do
  J="${COMBO%%:*}"; W="${COMBO##*:}"
  if [ "$J" = 21 ]; then export JAVA_HOME="$JDK21"; else export JAVA_HOME="$JDK17"; fi
  TAG="java$J-web3j$W"; OUT="../../evidence/java-matrix/$TAG"; mkdir -p "$OUT"
  docker restart freelax-anvil >/dev/null 2>&1; sleep 3
  "$MVN" -B -ntp -q clean -Djava.version=$J -Dweb3j.version=$W dependency:tree -Dincludes=com.fasterxml.jackson.core,tools.jackson.core,org.web3j,com.squareup.okhttp3 -DoutputFile="$OUT/tree.txt" >/dev/null 2>&1
  "$MVN" -B -ntp -Djava.version=$J -Dweb3j.version=$W -Devidence.dir="$OUT" test > "$OUT/surefire.log" 2>&1
  echo "$TAG exit=$? $(grep -a -E 'Tests run:.*Fail' "$OUT/surefire.log" | tail -1)"
  rm -f "${TEMP:-/tmp}/phase1-eip712-key.txt" "${TEMP:-/tmp}/phase1-eip712-typed.json"
done
