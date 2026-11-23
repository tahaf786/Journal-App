#!/usr/bin/env bash
# Runs one k6 test from the official grafana/k6 image, attached to the compose network.
# Usage: loadtest/run.sh read  <on|off> <vus> <run-number>
#        loadtest/run.sh write <on|off> <vus> <run-number>
# The cache mode is only a label for the output file: switch it first with
#   APP_CACHE_ENABLED=true|false docker compose up -d app
# Set BENCH_TAG=v2- to add a label after the kind in the file names.
# Raw summaries are written to docs/benchmarks/raw/.
set -euo pipefail
export MSYS_NO_PATHCONV=1

KIND="$1"; CACHE="$2"; VUS="$3"; RUN="$4"
ROOT="$(cd "$(dirname "$0")/.." && pwd -W 2>/dev/null || pwd)"
RAW="$ROOT/docs/benchmarks/raw"
mkdir -p "$RAW"
NAME="$KIND-${BENCH_TAG:-}cache-$CACHE-vus$VUS-run$RUN"
NETWORK="${COMPOSE_NETWORK:-journal-app_default}"

LAG_FILE="$RAW/$NAME.kafka-lag.txt"
total_lag() {
  docker exec journal-app-kafka-1 /opt/kafka/bin/kafka-consumer-groups.sh \
    --bootstrap-server localhost:9092 --describe --group journal-stats 2>/dev/null \
    | awk '$1=="journal-stats" && $2=="journal.entry-events" {s+=$6} END {print s+0}'
}
if [ "$KIND" = "write" ]; then
  # Start every write run with an empty backlog so runs do not contaminate each other.
  for _ in $(seq 1 900); do [ "$(total_lag)" = "0" ] && break; sleep 2; done
  : > "$LAG_FILE"
  ( while true; do
      echo "$(date +%s) $(docker exec journal-app-kafka-1 /opt/kafka/bin/kafka-consumer-groups.sh \
        --bootstrap-server localhost:9092 --describe --group journal-stats 2>/dev/null \
        | awk '$1=="journal-stats" && $2=="journal.entry-events" {s+=$6} END {print s+0}')" >> "$LAG_FILE"
      sleep 3
    done ) &
  SAMPLER=$!
  trap 'kill $SAMPLER 2>/dev/null || true' EXIT
fi

docker run --rm --network "$NETWORK" \
  -v "$ROOT/loadtest:/scripts:ro" -v "$RAW:/out" \
  -e VUS="$VUS" -e WARMUP="${WARMUP:-20s}" -e MEASURE="${MEASURE:-60s}" \
  grafana/k6:latest run --quiet --summary-export="/out/$NAME.json" "/scripts/$KIND.js" \
  > "$RAW/$NAME.console.txt" 2>&1 || echo "k6 exited non-zero (thresholds or errors): see $NAME.console.txt"

if [ "$KIND" = "write" ]; then
  kill $SAMPLER 2>/dev/null || true
  FINAL=-1
  for _ in $(seq 1 900); do
    FINAL=$(total_lag)
    [ "$FINAL" = "0" ] && break
    sleep 2
  done
  echo "$(date +%s) final-lag $FINAL" >> "$LAG_FILE"
fi
echo "done $NAME"
