#!/usr/bin/env bash
# Part B tasks A+B: Spring Boot 3.5.16 web context + web3j 6.1.0 across {Java 21, Java 17} x {no override, annotations22, bom22}.
# Read-only calls to Fuji (AVALANCHE_FUJI) only. Writes evidence/web-matrix/<java>-<profile>.{log,tree.txt,json}.
# Env: MVN (path to mvn), JDK21, JDK17 (JAVA_HOME dirs, Windows paths ok).
set -uo pipefail
cd "$(dirname "$0")/../prototypes/java-web3j-web"
OUT="../../evidence/web-matrix"; mkdir -p "$OUT"
MVN="${MVN:-mvn}"
JDK21="${JDK21:-C:\\Program Files\\Java\\jdk-21}"
JDK17="${JDK17:?set JDK17 to a JDK 17 home directory}"
for J in ${JAVAS:-21 17}; do
  if [ "$J" = 21 ]; then export JAVA_HOME="$JDK21"; else export JAVA_HOME="$JDK17"; fi
  for P in ${PROFILES:-none annotations22 bom22}; do
    PROF=""; [ "$P" != none ] && PROF="-P$P"
    TAG="java$J-$P${W3J:+-web3j$W3J}"; WV=""; [ -n "${W3J:-}" ] && WV="-Dweb3j.version=$W3J"
    "$MVN" -B -ntp -q clean $PROF -Djava.version=$J $WV dependency:tree -Dincludes=com.fasterxml.jackson.core,tools.jackson.core,org.web3j,com.squareup.okhttp3 -DoutputFile="$OUT/$TAG.tree.txt" >/dev/null 2>&1
    "$MVN" -B -ntp $PROF -Djava.version=$J $WV -Devidence.file="$OUT/$TAG.json" test > "$OUT/$TAG.log" 2>&1
    RC=$?
    echo "$TAG exit=$RC $(grep -a -E 'Tests run:.*Fail' "$OUT/$TAG.log" | tail -1)"
  done
done
