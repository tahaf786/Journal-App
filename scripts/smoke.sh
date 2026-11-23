#!/usr/bin/env bash
# End-to-end check against a running stack (docker compose up -d --build).
# Usage: scripts/smoke.sh [base-url]
set -euo pipefail

BASE="${1:-${BASE_URL:-http://localhost:8080}}"
API="$BASE/api/v1"
PASSWORD="smoke-test-password"
SUFFIX="$(date +%s)-$RANDOM"
ALICE="alice-$SUFFIX"
BOB="bob-$SUFFIX"

fail() { echo "FAIL: $*" >&2; exit 1; }
step() { echo "ok: $*"; }

# Prints the response body, then the HTTP status on the last line.
call() {
  local method="$1" url="$2" token="${3:-}" body="${4:-}"
  local args=(-sS -X "$method" -w '\n%{http_code}' -H 'Content-Type: application/json')
  [ -n "$token" ] && args+=(-H "Authorization: Bearer $token")
  [ -n "$body" ] && args+=(-d "$body")
  curl "${args[@]}" "$url"
}

status_of() { printf '%s' "$1" | tail -n1; }
body_of()   { printf '%s' "$1" | sed '$d'; }
field()     { printf '%s' "$1" | grep -o "\"$2\":\"[^\"]*\"" | head -n1 | cut -d'"' -f4; }
number()    { printf '%s' "$1" | grep -o "\"$2\":[0-9]*" | head -n1 | cut -d: -f2; }

expect() { # expect <actual-status> <expected-status> <label>
  [ "$1" = "$2" ] || fail "$3: expected HTTP $2, got $1"
  step "$3 -> $2"
}

wait_for_count() { # wait_for_count <token> <expected>
  for _ in $(seq 1 30); do
    local resp count
    resp="$(call GET "$API/users/me/stats" "$1")"
    count="$(number "$(body_of "$resp")" entryCount)"
    [ "$count" = "$2" ] && return 0
    sleep 1
  done
  fail "stats entryCount did not reach $2"
}

register_and_login() { # prints the access token
  local resp
  resp="$(call POST "$API/auth/register" "" "{\"username\":\"$1\",\"password\":\"$PASSWORD\"}")"
  expect "$(status_of "$resp")" 201 "register $1" >&2
  resp="$(call POST "$API/auth/login" "" "{\"username\":\"$1\",\"password\":\"$PASSWORD\"}")"
  expect "$(status_of "$resp")" 200 "login $1" >&2
  field "$(body_of "$resp")" accessToken
}

resp="$(call GET "$API/entries")"
expect "$(status_of "$resp")" 401 "unauthenticated request is rejected"

ALICE_TOKEN="$(register_and_login "$ALICE")"
[ -n "$ALICE_TOKEN" ] || fail "no access token for $ALICE"

resp="$(call POST "$API/entries" "$ALICE_TOKEN" '{"title":"Smoke entry","content":"written by the smoke test about lighthouses"}')"
expect "$(status_of "$resp")" 201 "create entry"
ENTRY_ID="$(field "$(body_of "$resp")" id)"
[ -n "$ENTRY_ID" ] || fail "no entry id returned"

resp="$(call GET "$API/entries/$ENTRY_ID" "$ALICE_TOKEN")"
expect "$(status_of "$resp")" 200 "read entry"
resp="$(call GET "$API/entries/$ENTRY_ID" "$ALICE_TOKEN")"
expect "$(status_of "$resp")" 200 "read entry again (cached path)"

resp="$(call PUT "$API/entries/$ENTRY_ID" "$ALICE_TOKEN" '{"title":"Smoke entry v2","content":"updated"}')"
expect "$(status_of "$resp")" 200 "update entry"
[ "$(field "$(body_of "$resp")" title)" = "Smoke entry v2" ] || fail "update was not applied"
resp="$(call GET "$API/entries/$ENTRY_ID" "$ALICE_TOKEN")"
[ "$(field "$(body_of "$resp")" title)" = "Smoke entry v2" ] || fail "read after update returned stale data"
step "read after update returns new title"

resp="$(call GET "$API/entries?size=5&sort=date,desc" "$ALICE_TOKEN")"
expect "$(status_of "$resp")" 200 "list entries"
body_of "$resp" | grep -q "$ENTRY_ID" || fail "created entry missing from list"

resp="$(call GET "$API/entries?q=updated" "$ALICE_TOKEN")"
expect "$(status_of "$resp")" 200 "search entries"
body_of "$resp" | grep -q "$ENTRY_ID" || fail "search did not find the entry"

wait_for_count "$ALICE_TOKEN" 1
step "stats reflect the created entry"

BOB_TOKEN="$(register_and_login "$BOB")"
resp="$(call GET "$API/entries/$ENTRY_ID" "$BOB_TOKEN")"
case "$(status_of "$resp")" in
  403|404) step "second user cannot read the first user's entry ($(status_of "$resp"))" ;;
  *) fail "second user got HTTP $(status_of "$resp") on another user's entry" ;;
esac

resp="$(call DELETE "$API/entries/$ENTRY_ID" "$ALICE_TOKEN")"
expect "$(status_of "$resp")" 204 "delete entry"
resp="$(call GET "$API/entries/$ENTRY_ID" "$ALICE_TOKEN")"
expect "$(status_of "$resp")" 404 "read deleted entry"

wait_for_count "$ALICE_TOKEN" 0
step "stats reflect the deleted entry"

resp="$(call GET "$BASE/actuator/health")"
expect "$(status_of "$resp")" 200 "health endpoint"

echo "SMOKE TEST PASSED"
