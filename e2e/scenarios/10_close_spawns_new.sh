#!/usr/bin/env bash
# 10_close_spawns_new.sh — Permanently close, then inbound spawns new ticket
set -euo pipefail

SCENARIO="10_close_spawns_new"
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

EXT_ID="e2e-10-$(random_token)"
CUSTOMER_EMAIL="e2e-close-$(random_token)@e2e.test"

# ── 1. Create ticket ──────────────────────────────────────────────────────────
info "${SCENARIO}: create ticket"
http_unauth POST "/v2/webhook/CUSTOM/${E2E_TEST_WEBHOOK_TOKEN}" \
  "{\"externalTicketId\":\"${EXT_ID}\",\"customerIdentifier\":\"${CUSTOMER_EMAIL}\",\"message\":\"My issue\",\"eventType\":\"NEW_TICKET\"}"
_check "webhook → 2xx" expect_2xx

TICKET_ID=""
poll_until 15 find_ticket_by_ext_id "${EXT_ID}" || { fail "${SCENARIO}: ticket not created"; exit 1; }
pass "${SCENARIO}: ticket created id=${TICKET_ID}"

# ── 2. Permanently close ──────────────────────────────────────────────────────
info "${SCENARIO}: permanently close ticket"
http POST "/api/desk/tickets/${TICKET_ID}/close"
_check "close → 200 or 204" expect_ok

http GET "/api/desk/tickets/${TICKET_ID}"
_check "status=CLOSED" expect_json '.status' "CLOSED"

closed_at=$(jq_extract '.closedAt')
if [ -z "$closed_at" ] || [ "$closed_at" = "null" ]; then
  fail "${SCENARIO}: closedAt not set"
  _fail=$((_fail + 1))
else
  pass "${SCENARIO}: closedAt set"
fi

# ── 3. Send another inbound — should spawn NEW ticket ─────────────────────────
info "${SCENARIO}: send inbound after permanent close"
http_unauth POST "/v2/webhook/CUSTOM/${E2E_TEST_WEBHOOK_TOKEN}" \
  "{\"externalTicketId\":\"${EXT_ID}\",\"customerIdentifier\":\"${CUSTOMER_EMAIL}\",\"message\":\"I have a new question\",\"eventType\":\"NEW_MESSAGE\"}"
_check "second webhook → 2xx" expect_2xx

sleep 3

# ── 4. Look for new ticket ────────────────────────────────────────────────────
info "${SCENARIO}: search for new (spawned) ticket"
# List all tickets and find one with same externalNativeId or customerIdentifier but different id
http GET "/api/desk/tickets?limit=100"
NEW_TICKET_ID=$(jq_extract "[.[] | select(.customerIdentifier == \"${CUSTOMER_EMAIL}\" and .id != \"${TICKET_ID}\")] | .[0].id")

if [ -z "$NEW_TICKET_ID" ] || [ "$NEW_TICKET_ID" = "null" ]; then
  fail "${SCENARIO}: no new ticket spawned after inbound on CLOSED ticket"
  _fail=$((_fail + 1))
else
  pass "${SCENARIO}: new ticket spawned id=${NEW_TICKET_ID}"

  # Check new ticket has parentTicketId pointing to original
  http GET "/api/desk/tickets/${NEW_TICKET_ID}"
  parent_id=$(jq_extract '.parentTicketId')
  if [ "$parent_id" = "$TICKET_ID" ]; then
    pass "${SCENARIO}: parentTicketId=${parent_id} points to original"
  else
    fail "${SCENARIO}: parentTicketId=${parent_id} — expected ${TICKET_ID}"
    _fail=$((_fail + 1))
  fi

  # Original ticket must still be CLOSED
  http GET "/api/desk/tickets/${TICKET_ID}"
  _check "original ticket still CLOSED" expect_json '.status' "CLOSED"
fi

info "${SCENARIO}: fail=${_fail}"
[ "$_fail" -eq 0 ]
