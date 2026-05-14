#!/usr/bin/env bash
# 09_solve_reopen.sh — Solve then reopen within window (same ticket)
set -euo pipefail

SCENARIO="09_solve_reopen"
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

EXT_ID="e2e-09-$(random_token)"
CUSTOMER_EMAIL="e2e-reopen-$(random_token)@e2e.test"

# ── 1. Create ticket ──────────────────────────────────────────────────────────
info "${SCENARIO}: create ticket"
http_unauth POST "/v2/webhook/CUSTOM/${E2E_TEST_WEBHOOK_TOKEN}" \
  "{\"externalTicketId\":\"${EXT_ID}\",\"customerIdentifier\":\"${CUSTOMER_EMAIL}\",\"message\":\"My issue\",\"eventType\":\"NEW_TICKET\"}"
_check "webhook → 2xx" expect_2xx

TICKET_ID=""
poll_until 15 find_ticket_by_ext_id "${EXT_ID}" || { fail "${SCENARIO}: ticket not created"; exit 1; }
pass "${SCENARIO}: ticket created id=${TICKET_ID}"

# ── 2. Solve ticket ───────────────────────────────────────────────────────────
info "${SCENARIO}: solve ticket"
http POST "/api/desk/tickets/${TICKET_ID}/solve"
_check "solve → 200 or 204" expect_ok

http GET "/api/desk/tickets/${TICKET_ID}"
_check "status=SOLVED" expect_json '.status' "SOLVED"

solved_at=$(jq_extract '.solvedAt')
if [ -z "$solved_at" ] || [ "$solved_at" = "null" ]; then
  fail "${SCENARIO}: solvedAt not set"
  _fail=$((_fail + 1))
else
  pass "${SCENARIO}: solvedAt set"
fi

# ── 3. Send another inbound (reopen within window) ────────────────────────────
info "${SCENARIO}: send inbound to reopen solved ticket"
http_unauth POST "/v2/webhook/CUSTOM/${E2E_TEST_WEBHOOK_TOKEN}" \
  "{\"externalTicketId\":\"${EXT_ID}\",\"customerIdentifier\":\"${CUSTOMER_EMAIL}\",\"message\":\"Actually I have another question\",\"eventType\":\"NEW_MESSAGE\"}"
_check "reopen webhook → 2xx" expect_2xx

# Poll for status change
sleep 3

http GET "/api/desk/tickets/${TICKET_ID}"
status_after=$(jq_extract '.status')
case "$status_after" in
  OPEN | NEW | WAITING_ON_CUSTOMER)
    pass "${SCENARIO}: ticket reopened, status=${status_after}"
    ;;
  *)
    fail "${SCENARIO}: unexpected status after reopen: ${status_after}"
    _fail=$((_fail + 1))
    ;;
esac

reopen_count=$(jq_extract '.reopenCount')
if [ "${reopen_count:-0}" -gt 0 ]; then
  pass "${SCENARIO}: reopenCount=${reopen_count}"
else
  fail "${SCENARIO}: reopenCount not incremented (got ${reopen_count:-0})"
  _fail=$((_fail + 1))
fi

parent_id=$(jq_extract '.parentTicketId')
if [ -z "$parent_id" ] || [ "$parent_id" = "null" ]; then
  pass "${SCENARIO}: parentTicketId=null (same ticket, not spawned)"
else
  fail "${SCENARIO}: parentTicketId set on reopened ticket — expected null (same ticket)"
  _fail=$((_fail + 1))
fi

info "${SCENARIO}: fail=${_fail}"
[ "$_fail" -eq 0 ]
