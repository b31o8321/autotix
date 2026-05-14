#!/usr/bin/env bash
# 08_internal_note.sh — Internal note does not change ticket status or notify customer
set -euo pipefail

SCENARIO="08_internal_note"
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

if [ -z "${E2E_TEST_WEBHOOK_TOKEN:-}" ]; then
  fail "${SCENARIO}: no webhook token"
  exit 1
fi

EXT_ID="e2e-08-$(random_token)"
CUSTOMER_EMAIL="e2e-note-$(random_token)@e2e.test"

# ── 1. Create ticket via inbound ──────────────────────────────────────────────
info "${SCENARIO}: create ticket via inbound"
http_unauth POST "/v2/webhook/CUSTOM/${E2E_TEST_WEBHOOK_TOKEN}" \
  "{\"externalTicketId\":\"${EXT_ID}\",\"customerIdentifier\":\"${CUSTOMER_EMAIL}\",\"message\":\"Need help\",\"eventType\":\"NEW_TICKET\"}"
_check "webhook → 2xx" expect_2xx

TICKET_ID=""
poll_until 15 find_ticket_by_ext_id "${EXT_ID}" || { fail "${SCENARIO}: ticket not created"; exit 1; }
pass "${SCENARIO}: ticket created id=${TICKET_ID}"

# Note status before internal note
http GET "/api/desk/tickets/${TICKET_ID}"
status_before=$(jq_extract '.status')
info "${SCENARIO}: status before note = ${status_before}"

# ── 2. POST internal note ─────────────────────────────────────────────────────
info "${SCENARIO}: POST internal note"
http POST "/api/desk/tickets/${TICKET_ID}/reply" \
  '{"content":"Internal note: AI not handling this — assigning to Carol","closeAfter":false,"internal":true}'
_check "internal note → 200 or 204" expect_ok

# ── 3. Status must NOT have changed to WAITING_ON_CUSTOMER ────────────────────
http GET "/api/desk/tickets/${TICKET_ID}"
status_after=$(jq_extract '.status')
info "${SCENARIO}: status after note = ${status_after}"

if [ "$status_after" = "WAITING_ON_CUSTOMER" ]; then
  fail "${SCENARIO}: ticket status changed to WAITING_ON_CUSTOMER after internal note (should not)"
  _fail=$((_fail + 1))
else
  pass "${SCENARIO}: status remained ${status_after} (not WAITING_ON_CUSTOMER)"
fi

# ── 4. Message has visibility=INTERNAL ────────────────────────────────────────
http GET "/api/desk/tickets/${TICKET_ID}/messages"
internal_count=$(jq_extract '[.[] | select(.visibility == "INTERNAL")] | length')
if [ "${internal_count:-0}" -gt 0 ]; then
  pass "${SCENARIO}: ${internal_count} INTERNAL message(s) found"
else
  fail "${SCENARIO}: no INTERNAL visibility messages found"
  _fail=$((_fail + 1))
fi

info "${SCENARIO}: fail=${_fail}"
[ "$_fail" -eq 0 ]
