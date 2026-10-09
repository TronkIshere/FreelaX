#!/bin/bash
# start|stop the local RPC proxy (127.0.0.1:9133 -> 127.0.0.1:9123) used by the outage drill.
cd "$(dirname "$0")"
PID=/tmp/freelax-rpc-proxy.pid
case "$1" in
  start)
    [ -f "$PID" ] && kill -0 "$(cat $PID)" 2>/dev/null && exit 0
    nohup setsid node local-rpc-proxy.mjs 9133 9123 > /tmp/freelax-rpc-proxy.log 2>&1 < /dev/null &
    echo $! > "$PID"
    for i in $(seq 1 20); do
      curl -fsS -m 2 -H 'Content-Type: application/json' --data '{"jsonrpc":"2.0","id":1,"method":"getHealth"}' \
        http://127.0.0.1:9133 >/dev/null 2>&1 && exit 0; sleep 1; done
    exit 1 ;;
  stop)
    [ -f "$PID" ] && kill "$(cat $PID)" 2>/dev/null; rm -f "$PID"
    for i in $(seq 1 10); do ss -ltn | grep -q ':9133 ' || exit 0; sleep 1; done; exit 1 ;;
esac
