#!/usr/bin/env bash
# 11_tags_lifecycle.sh — Add/remove tags, verify tag definition library
set -euo pipefail

SCENARIO="11_tags_lifecycle"
_fail=0

_check() {
  local label="$1"
  shift
  if "$@"; then
    pass "${SCENARIO}: ${label}"
  else
    fail "${SCENARIO}: ${label}"
    _fail=$((_fail + 1))
  fi
}

if [ -f /tmp/e2e-channel.env ]; then
  # shellcheck disable=SC1091
  source /tmp/e2e-channel.env
fi

# Use ticket from scenario 04 or find any open ticket
TICKET_ID="${E2E_TICKET_04:-}"
if [ -z "$TICKET_ID" ] || [ "$TICKET_ID" = "null" ]; then
  http GET "/api/desk/tickets?limit=10"
  TICKET_ID=$(jq_extract '.[0].id')
fi

if [ -z "$TICKET_ID" ] || [ "$TICKET_ID" = "null" ]; then
  fail "${SCENARIO}: no ticket available"
  exit 1
fi
info "${SCENARIO}: using ticket ${TICKET_ID}"

TAG1="e2e-refund-$(random_token | cut -c1-6)"
TAG2="e2e-urgent-$(random_token | cut -c1-6)"

# ── 1. Add two tags ───────────────────────────────────────────────────────────
info "${SCENARIO}: add tags ${TAG1}, ${TAG2}"
http POST "/api/desk/tickets/${TICKET_ID}/tags" \
  "{\"add\":[\"${TAG1}\",\"${TAG2}\"],\"remove\":[]}"
_check "add tags → 200 or 204" expect_ok

http GET "/api/desk/tickets/${TICKET_ID}"
tags_json=$(jq_extract '.tags')
_check "tag1 present" assert_contains "tags" "$tags_json" "$TAG1"
_check "tag2 present" assert_contains "tags" "$tags_json" "$TAG2"

# ── 2. Remove one tag ─────────────────────────────────────────────────────────
info "${SCENARIO}: remove tag ${TAG2}"
http POST "/api/desk/tickets/${TICKET_ID}/tags" \
  "{\"add\":[],\"remove\":[\"${TAG2}\"]}"
_check "remove tag → 200 or 204" expect_ok

http GET "/api/desk/tickets/${TICKET_ID}"
tags_after=$(jq_extract '.tags')
_check "tag1 still present" assert_contains "tags" "$tags_after" "$TAG1"

case "$tags_after" in
  *"$TAG2"*)
    fail "${SCENARIO}: removed tag ${TAG2} still present in ${tags_after}"
    _fail=$((_fail + 1))
    ;;
  *)
    pass "${SCENARIO}: removed tag ${TAG2} no longer present"
    ;;
esac

# ── 3. Verify tags appear in tag library (suggestions) ───────────────────────
info "${SCENARIO}: check tag suggestions"
http GET "/api/desk/tags/suggestions"
_check "suggestions → 200" expect_status 200

suggestions=$(jq_extract '[.[].name]')
_check "tag1 in suggestions" assert_contains "suggestions" "$suggestions" "$TAG1"
_check "tag2 in suggestions" assert_contains "suggestions" "$suggestions" "$TAG2"

info "${SCENARIO}: fail=${_fail}"
[ "$_fail" -eq 0 ]
