#!/usr/bin/env bash
# 07_resume_ai_admin.sh — Resume AI (admin-only) after escalation
set -euo pipefail

SCENARIO="07_resume_ai_admin"
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

# Load channel env
if [ -f /tmp/e2e-channel.env ]; then
  # shellcheck disable=SC1091
  source /tmp/e2e-channel.env
fi

TICKET_ID="${E2E_TICKET_06:-}"
if [ -z "$TICKET_ID" ] || [ "$TICKET_ID" = "null" ]; then
  # Try to find an escalated ticket
  http GET "/api/desk/tickets?limit=50"
  TICKET_ID=$(jq_extract '[.[] | select(.aiSuspended == true)] | .[0].id')
  if [ -z "$TICKET_ID" ] || [ "$TICKET_ID" = "null" ]; then
    fail "${SCENARIO}: no escalated ticket found (run scenario 06 first)"
    exit 1
  fi
  note "${SCENARIO}: using fallback ticket ${TICKET_ID}"
fi

# ── 1. Verify ticket is currently escalated ───────────────────────────────────
info "${SCENARIO}: verify ticket ${TICKET_ID} is escalated"
http GET "/api/desk/tickets/${TICKET_ID}"
_check "ticket detail → 200" expect_status 200
_check "aiSuspended=true before resume" expect_json '.aiSuspended' "true"

# ── 2. POST resume-ai ─────────────────────────────────────────────────────────
info "${SCENARIO}: POST resume-ai (admin)"
http POST "/api/desk/tickets/${TICKET_ID}/resume-ai"
_check "resume-ai → 200 or 204" expect_ok

# ── 3. Verify aiSuspended=false ───────────────────────────────────────────────
http GET "/api/desk/tickets/${TICKET_ID}"
_check "aiSuspended=false after resume" expect_json '.aiSuspended' "false"

# ── 4. Check activity for AI_RESUMED event ────────────────────────────────────
http GET "/api/desk/tickets/${TICKET_ID}/activity"
resumed_event=$(jq_extract '[.[] | select(.action == "AI_RESUMED")] | length')
if [ "${resumed_event:-0}" -gt 0 ]; then
  pass "${SCENARIO}: AI_RESUMED activity found"
else
  fail "${SCENARIO}: AI_RESUMED activity not found"
  _fail=$((_fail + 1))
fi

info "${SCENARIO}: fail=${_fail}"
[ "$_fail" -eq 0 ]
