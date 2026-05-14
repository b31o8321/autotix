#!/usr/bin/env bash
# 14_sla_breach.sh — SLA breach detection
#
# WARNING: This test sets SLA firstResponseMinutes/resolutionMinutes to 1 minute
# for HIGH priority, then waits for the SLA scheduler to tick (default 60s interval).
# Total wait: up to 130s. This is expected (worst case = 2 scheduler cycles).
#
# RESTORES the SLA policy to reasonable defaults at the end.
set -euo pipefail

SCENARIO="14_sla_breach"
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

# ── 1. Set HIGH priority SLA to 1 minute so it immediately breaches ──────────
note "${SCENARIO}: setting HIGH priority SLA to 1 minute (will be restored)"
http PUT "/api/admin/sla/HIGH" \
  '{"name":"E2E breach test","firstResponseMinutes":1,"resolutionMinutes":1,"enabled":true}'
_check "set SLA policy → 200" expect_status 200

# ── 2. Create ticket ──────────────────────────────────────────────────────────
EXT_ID="e2e-14-$(random_token)"
CUSTOMER_EMAIL="e2e-sla-$(random_token)@e2e.test"

info "${SCENARIO}: create ticket"
http_unauth POST "/v2/webhook/CUSTOM/${E2E_TEST_WEBHOOK_TOKEN}" \
  "{\"externalTicketId\":\"${EXT_ID}\",\"customerIdentifier\":\"${CUSTOMER_EMAIL}\",\"message\":\"SLA test ticket\",\"eventType\":\"NEW_TICKET\"}"
_check "webhook → 2xx" expect_2xx

TICKET_ID=""
poll_until 15 find_ticket_by_ext_id "${EXT_ID}" || { fail "${SCENARIO}: ticket not created"; exit 1; }
pass "${SCENARIO}: ticket created id=${TICKET_ID}"

# ── 3. Set priority to HIGH so the 1-minute policy applies ───────────────────
info "${SCENARIO}: set priority=HIGH"
http PUT "/api/desk/tickets/${TICKET_ID}/priority?value=HIGH"
_check "priority → 200 or 204" expect_ok

# ── 4. Wait for SLA scheduler tick (up to 75s) ───────────────────────────────
note "${SCENARIO}: waiting up to 75s for SLA scheduler to mark breach..."

_check_breached() {
  http GET "/api/desk/tickets/${TICKET_ID}"
  breached=$(jq_extract '.slaBreached')
  [ "$breached" = "true" ]
}

# Wait up to 130s — scheduler runs every 60s; worst case we need 60s + margin after ticket creation
if poll_until 130 _check_breached; then
  pass "${SCENARIO}: slaBreached=true"
else
  note "${SCENARIO}: slaBreached not set after 130s — SLA scheduler may have longer interval or there's a bug"
  http GET "/api/desk/tickets/${TICKET_ID}"
  due=$(jq_extract '.firstResponseDueAt')
  if [ -n "$due" ] && [ "$due" != "null" ]; then
    note "${SCENARIO}: firstResponseDueAt=${due} (overdue — scheduler should have caught this)"
  fi
  # Soft fail with note — this is a known limitation documented in README
  note "${SCENARIO}: SOFT FAIL — SLA breach check timed out"
  _fail=$((_fail + 1))
fi

# ── 5. Restore SLA policy ─────────────────────────────────────────────────────
info "${SCENARIO}: restoring HIGH SLA policy"
http PUT "/api/admin/sla/HIGH" \
  '{"name":"High Priority SLA","firstResponseMinutes":60,"resolutionMinutes":480,"enabled":true}'
if [ "$LAST_HTTP_STATUS" = "200" ]; then
  pass "${SCENARIO}: HIGH SLA policy restored"
else
  note "${SCENARIO}: SLA restore returned ${LAST_HTTP_STATUS}"
fi

info "${SCENARIO}: fail=${_fail}"
[ "$_fail" -eq 0 ]
