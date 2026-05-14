#!/usr/bin/env bash
# 04_inbound_creates_ticket.sh — POST webhook, assert ticket appears
set -euo pipefail

SCENARIO="04_inbound_creates_ticket"
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

if [ -z "${E2E_TEST_CHANNEL_ID:-}" ] || [ -z "${E2E_TEST_WEBHOOK_TOKEN:-}" ]; then
  fail "${SCENARIO}: E2E_TEST_CHANNEL_ID / E2E_TEST_WEBHOOK_TOKEN not set (run scenario 03 first)"
  exit 1
fi

EXT_ID="e2e-04-$(random_token)"
CUSTOMER_EMAIL="alice-$(random_token)@e2e.test"
SUBJECT="E2E inbound test ${EXT_ID}"

# ── 1. POST webhook ───────────────────────────────────────────────────────────
info "${SCENARIO}: POST inbound webhook ext=${EXT_ID}"
http_unauth POST "/v2/webhook/CUSTOM/${E2E_TEST_WEBHOOK_TOKEN}" \
  "{\"externalTicketId\":\"${EXT_ID}\",\"customerIdentifier\":\"${CUSTOMER_EMAIL}\",\"subject\":\"${SUBJECT}\",\"message\":\"Hello from E2E test\",\"eventType\":\"NEW_TICKET\"}"
_check "webhook → 200 or 204" expect_2xx

# ── 2. Poll for ticket ────────────────────────────────────────────────────────
info "${SCENARIO}: polling for ticket (up to 10s)"

TICKET_ID=""
if poll_until 15 find_ticket_by_ext_id "${EXT_ID}"; then
  pass "${SCENARIO}: ticket found id=${TICKET_ID}"
else
  fail "${SCENARIO}: ticket not found after 15s"
  _fail=$((_fail + 1))
  exit 1
fi

# ── 3. Assert ticket fields ────────────────────────────────────────────────────
info "${SCENARIO}: GET ticket detail"
http GET "/api/desk/tickets/${TICKET_ID}"
_check "ticket detail → 200" expect_status 200

ticket_status=$(jq_extract '.status')
case "$ticket_status" in
  NEW | OPEN) pass "${SCENARIO}: status=${ticket_status} (acceptable)" ;;
  *)
    fail "${SCENARIO}: unexpected status=${ticket_status}"
    _fail=$((_fail + 1))
    ;;
esac

_check "customerIdentifier matches" expect_json '.customerIdentifier' "$CUSTOMER_EMAIL"

# ── 4. Assert at least 1 inbound message ──────────────────────────────────────
msg_count=$(jq_extract '[.messages[]? | select(.direction == "INBOUND")] | length')
if [ "${msg_count:-0}" -gt 0 ]; then
  pass "${SCENARIO}: ${msg_count} INBOUND message(s) in detail response"
else
  # Try messages endpoint
  http GET "/api/desk/tickets/${TICKET_ID}/messages"
  msg_count=$(jq_extract '[.[] | select(.direction == "INBOUND")] | length')
  if [ "${msg_count:-0}" -gt 0 ]; then
    pass "${SCENARIO}: ${msg_count} INBOUND message(s) via messages endpoint"
  else
    fail "${SCENARIO}: no INBOUND messages found"
    _fail=$((_fail + 1))
  fi
fi

# Save ticket id for downstream scenarios
export E2E_TICKET_04="${TICKET_ID}"
printf 'E2E_TICKET_04=%s\n' "${TICKET_ID}" >> /tmp/e2e-channel.env

info "${SCENARIO}: ticket_id=${TICKET_ID}"
[ "$_fail" -eq 0 ]
