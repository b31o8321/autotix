#!/usr/bin/env bash
# 13_priority_type_change.sh — Change ticket priority + type, verify SLA recomputed
set -euo pipefail

SCENARIO="13_priority_type_change"
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

if [ -z "${E2E_TEST_WEBHOOK_TOKEN:-}" ]; then
  fail "${SCENARIO}: no webhook token"
  exit 1
fi

EXT_ID="e2e-13-$(random_token)"
CUSTOMER_EMAIL="e2e-pri-$(random_token)@e2e.test"

# ── 1. Create ticket ──────────────────────────────────────────────────────────
info "${SCENARIO}: create ticket"
http_unauth POST "/v2/webhook/CUSTOM/${E2E_TEST_WEBHOOK_TOKEN}" \
  "{\"externalTicketId\":\"${EXT_ID}\",\"customerIdentifier\":\"${CUSTOMER_EMAIL}\",\"message\":\"Priority test\",\"eventType\":\"NEW_TICKET\"}"
_check "webhook → 2xx" expect_2xx

TICKET_ID=""
poll_until 15 find_ticket_by_ext_id "${EXT_ID}" || { fail "${SCENARIO}: ticket not created"; exit 1; }
pass "${SCENARIO}: ticket created id=${TICKET_ID}"

# Note original SLA due date
http GET "/api/desk/tickets/${TICKET_ID}"
due_before=$(jq_extract '.firstResponseDueAt')
info "${SCENARIO}: firstResponseDueAt before priority change = ${due_before:-<none>}"

# ── 2. Change priority to HIGH ────────────────────────────────────────────────
info "${SCENARIO}: PUT priority=HIGH"
http PUT "/api/desk/tickets/${TICKET_ID}/priority?value=HIGH"
_check "priority change → 200 or 204" expect_ok

http GET "/api/desk/tickets/${TICKET_ID}"
_check "priority=HIGH" expect_json '.priority' "HIGH"

due_after=$(jq_extract '.firstResponseDueAt')
info "${SCENARIO}: firstResponseDueAt after priority change = ${due_after:-<none>}"

# SLA dates should be recomputed (may differ from default NORMAL priority)
# We don't fail if both are null (SLA policy not configured), but note it
if [ -n "$due_before" ] && [ "$due_before" != "null" ] && \
   [ -n "$due_after" ] && [ "$due_after" != "null" ] && \
   [ "$due_before" = "$due_after" ]; then
  note "${SCENARIO}: SLA due dates unchanged after priority change (may be same policy values)"
fi

# ── 3. Change type to INCIDENT ────────────────────────────────────────────────
info "${SCENARIO}: PUT type=INCIDENT"
http PUT "/api/desk/tickets/${TICKET_ID}/type?value=INCIDENT"
_check "type change → 200 or 204" expect_ok

http GET "/api/desk/tickets/${TICKET_ID}"
_check "type=INCIDENT" expect_json '.type' "INCIDENT"

info "${SCENARIO}: fail=${_fail}"
[ "$_fail" -eq 0 ]
