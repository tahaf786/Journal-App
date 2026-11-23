#!/usr/bin/env bash
# Full benchmark sequence. Run from the repository root with the compose stack built:
#   loadtest/bench-all.sh
# Order: 3 rounds; each round runs the read mix with the cache OFF at every level, then ON at every level.
# Then 3 write runs. Raw summaries land in docs/benchmarks/raw/.
set -uo pipefail
export MSYS_NO_PATHCONV=1
LEVELS="${LEVELS:-50 200 400}"
WRITE_VUS="${WRITE_VUS:-20}"
export WARMUP="${WARMUP:-20s}" MEASURE="${MEASURE:-60s}"

switch_cache() { # switch_cache true|false
  APP_CACHE_ENABLED="$1" docker compose up -d --force-recreate app > /dev/null 2>&1
  for _ in $(seq 1 60); do
    [ "$(docker inspect -f '{{.State.Health.Status}}' journal-app-app-1)" = "healthy" ] && break
    sleep 3
  done
  echo "cache=$1 app=$(docker inspect -f '{{.State.Health.Status}}' journal-app-app-1) env=$(docker exec journal-app-app-1 printenv APP_CACHE_ENABLED)"
  sleep 10
}

for round in 1 2 3; do
  for mode in off on; do
    [ "$mode" = "on" ] && flag=true || flag=false
    switch_cache "$flag"
    for vus in $LEVELS; do
      loadtest/run.sh read "$mode" "$vus" "$round"
    done
  done
done

switch_cache true
for round in 1 2 3; do
  loadtest/run.sh write on "$WRITE_VUS" "$round"
done
echo ALL-DONE
