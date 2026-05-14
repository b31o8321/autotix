#!/usr/bin/env bash
# lib/common.sh — shared helpers for autotix E2E tests
# Compatible with bash 3.2 (macOS), zsh, bash 4+ (Linux)
# No mapfile, no ${var^^} — use tr for upper-case.

# ── Globals set by http/http_unauth ──────────────────────────────────────────
LAST_HTTP_STATUS=""
LAST_HTTP_BODY=""

# ── Color codes (only when stdout is a TTY) ──────────────────────────────────
if [ -t 1 ]; then
  C_GREEN='\033[0;32m'
  C_RED='\033[0;31m'
  C_YELLOW='\033[0;33m'
  C_CYAN='\033[0;36m'
  C_RESET='\033[0m'
  C_BOLD='\033[1m'
else
  C_GREEN=''
  C_RED=''
  C_YELLOW=''
  C_CYAN=''
  C_RESET=''
  C_BOLD=''
fi

# ── Logging ───────────────────────────────────────────────────────────────────
info()  { printf "${C_CYAN}  INFO${C_RESET}  %s\n" "$*"; }
pass()  { printf "${C_GREEN}  PASS${C_RESET}  %s\n" "$*"; }
fail()  { printf "${C_RED}  FAIL${C_RESET}  %s\n" "$*" >&2; }
note()  { printf "${C_YELLOW}  NOTE${C_RESET}  %s\n" "$*"; }

# ── HTTP helpers ──────────────────────────────────────────────────────────────
# http <METHOD> <PATH> [JSON_BODY]
# Sets LAST_HTTP_STATUS and LAST_HTTP_BODY. Uses ADMIN_TOKEN if set.
http() {
  local method="$1"
  local path="$2"
  local body="${3:-}"
  local url="${BASE_URL}${path}"

  local curl_args=(-s -w "\n__STATUS__:%{http_code}" -X "$method")
  curl_args+=(-H "Content-Type: application/json")

  if [ -n "${ADMIN_TOKEN:-}" ]; then
    curl_args+=(-H "Authorization: Bearer ${ADMIN_TOKEN}")
  fi

  if [ -n "$body" ]; then
    curl_args+=(--data-raw "$body")
  fi

  local raw
  raw=$(curl "${curl_args[@]}" "$url" 2>/dev/null)
  LAST_HTTP_STATUS=$(printf '%s' "$raw" | grep -o '__STATUS__:[0-9]*' | cut -d: -f2)
  LAST_HTTP_BODY=$(printf '%s' "$raw" | sed 's/__STATUS__:[0-9]*$//')
}

# http_unauth <METHOD> <PATH> [JSON_BODY] — same but never sends auth header
http_unauth() {
  local method="$1"
  local path="$2"
  local body="${3:-}"
  local url="${BASE_URL}${path}"

  local curl_args=(-s -w "\n__STATUS__:%{http_code}" -X "$method")
  curl_args+=(-H "Content-Type: application/json")

  if [ -n "$body" ]; then
    curl_args+=(--data-raw "$body")
  fi

  local raw
  raw=$(curl "${curl_args[@]}" "$url" 2>/dev/null)
  LAST_HTTP_STATUS=$(printf '%s' "$raw" | grep -o '__STATUS__:[0-9]*' | cut -d: -f2)
  LAST_HTTP_BODY=$(printf '%s' "$raw" | sed 's/__STATUS__:[0-9]*$//')
}

# http_download <URL> — download raw bytes; sets LAST_HTTP_STATUS, returns body via stdout
http_download() {
  local url="$1"
  local tmp
  tmp=$(mktemp)
  local status
  status=$(curl -s -o "$tmp" -w "%{http_code}" "$url" 2>/dev/null)
  LAST_HTTP_STATUS="$status"
  cat "$tmp"
  rm -f "$tmp"
}

# ── Assertions ────────────────────────────────────────────────────────────────

# expect_status <expected_code>
# Returns 0 if LAST_HTTP_STATUS matches, else prints failure context and returns 1.
expect_status() {
  local expected="$1"
  if [ "$LAST_HTTP_STATUS" != "$expected" ]; then
    fail "Expected HTTP $expected, got $LAST_HTTP_STATUS"
    fail "Body: $LAST_HTTP_BODY"
    return 1
  fi
  return 0
}

# expect_ok — accepts 200 or 204
expect_ok() {
  case "$LAST_HTTP_STATUS" in
    200|204) return 0 ;;
    *)
      fail "Expected HTTP 200 or 204, got $LAST_HTTP_STATUS"
      fail "Body: $LAST_HTTP_BODY"
      return 1
      ;;
  esac
}

# expect_2xx — accepts any 2xx status
expect_2xx() {
  case "$LAST_HTTP_STATUS" in
    2??) return 0 ;;
    *)
      fail "Expected HTTP 2xx, got $LAST_HTTP_STATUS"
      fail "Body: $LAST_HTTP_BODY"
      return 1
      ;;
  esac
}

# expect_json <jq_filter> <expected_value>
# Runs jq filter on LAST_HTTP_BODY; compares result (raw string) to expected.
expect_json() {
  local filter="$1"
  local expected="$2"
  local actual
  actual=$(printf '%s' "$LAST_HTTP_BODY" | jq -r "$filter" 2>/dev/null)
  if [ "$actual" != "$expected" ]; then
    fail "expect_json: filter='$filter' expected='$expected' got='$actual'"
    fail "Body: $LAST_HTTP_BODY"
    return 1
  fi
  return 0
}

# expect_json_contains <jq_filter> <substring>
# Checks that the jq-extracted value contains the given substring.
expect_json_contains() {
  local filter="$1"
  local substring="$2"
  local actual
  actual=$(printf '%s' "$LAST_HTTP_BODY" | jq -r "$filter" 2>/dev/null)
  case "$actual" in
    *"$substring"*) return 0 ;;
    *)
      fail "expect_json_contains: filter='$filter' substring='$substring' got='$actual'"
      fail "Body: $LAST_HTTP_BODY"
      return 1
      ;;
  esac
}

# assert_eq <label> <actual> <expected>
assert_eq() {
  local label="$1"
  local actual="$2"
  local expected="$3"
  if [ "$actual" != "$expected" ]; then
    fail "assert_eq '$label': expected='$expected' got='$actual'"
    return 1
  fi
  return 0
}

# assert_contains <label> <haystack> <needle>
assert_contains() {
  local label="$1"
  local haystack="$2"
  local needle="$3"
  case "$haystack" in
    *"$needle"*) return 0 ;;
    *)
      fail "assert_contains '$label': '$needle' not found in '$haystack'"
      return 1
      ;;
  esac
}

# ── Utilities ─────────────────────────────────────────────────────────────────

# random_token — 16 hex chars (no ${var^^}, no bash-isms)
random_token() {
  od -An -N8 -tx1 /dev/urandom | tr -d ' \n'
}

# random_email — e2e-<random>@example.test
random_email() {
  printf 'e2e-%s@example.test' "$(random_token)"
}

# poll_until <max_seconds> <shell_command...>
# Calls the command every 2 seconds up to max_seconds; returns 0 on first success.
poll_until() {
  local max_sec="$1"
  shift
  local elapsed=0
  while [ "$elapsed" -lt "$max_sec" ]; do
    if "$@"; then
      return 0
    fi
    sleep 2
    elapsed=$((elapsed + 2))
  done
  return 1
}

# jq_extract <filter> [body]  — extract field from body (or LAST_HTTP_BODY)
jq_extract() {
  local filter="$1"
  local body="${2:-$LAST_HTTP_BODY}"
  printf '%s' "$body" | jq -r "$filter" 2>/dev/null
}

# find_ticket_by_ext_id <ext_id>
# Lists tickets and filters client-side by externalNativeId.
# Sets TICKET_ID on success. Returns 0 if found, 1 if not.
find_ticket_by_ext_id() {
  local ext_id="$1"
  http GET "/api/desk/tickets?limit=100"
  local found_id
  found_id=$(jq_extract "[.[] | select(.externalNativeId == \"${ext_id}\")] | .[0].id")
  if [ -n "$found_id" ] && [ "$found_id" != "null" ]; then
    TICKET_ID="$found_id"
    return 0
  fi
  return 1
}
