#!/usr/bin/env bash
# 26_reports_summary.sh — Verify GET /api/desk/reports/summary endpoint
set -euo pipefail

SCENARIO="26_reports_summary"
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

# Load channel env from prior scenarios
if [ -f /tmp/e2e-channel.env ]; then
  # shellcheck disable=SC1091
  source /tmp/e2e-channel.env
fi

if [ -z "${E2E_TEST_WEBHOOK_TOKEN:-}" ]; then
  fail "${SCENARIO}: no webhook token -- run scenario 03 first"
  exit 1
fi

EXT_ID_A="e2e-26a-$(random_token)"
EXT_ID_B="e2e-26b-$(random_token)"
CUSTOMER_A="e2e-26a-$(random_token)@e2e.test"
CUSTOMER_B="e2e-26b-$(random_token)@e2e.test"

# 1. Create two tickets
info "${SCENARIO}: create ticket A"
http_unauth POST "/v2/webhook/CUSTOM/${E2E_TEST_WEBHOOK_TOKEN}" \
  "{\"externalTicketId\":\"${EXT_ID_A}\",\"customerIdentifier\":\"${CUSTOMER_A}\",\"subject\":\"Reports test A\",\"message\":\"Hello A\",\"eventType\":\"NEW_TICKET\"}"
_check "webhook A -> 2xx" expect_2xx

info "${SCENARIO}: create ticket B"
http_unauth POST "/v2/webhook/CUSTOM/${E2E_TEST_WEBHOOK_TOKEN}" \
  "{\"externalTicketId\":\"${EXT_ID_B}\",\"customerIdentifier\":\"${CUSTOMER_B}\",\"subject\":\"Reports test B\",\"message\":\"Hello B\",\"eventType\":\"NEW_TICKET\"}"
_check "webhook B -> 2xx" expect_2xx

# 2. Wait for ticket A and solve it
TICKET_ID=""
info "${SCENARIO}: polling for ticket A"
if poll_until 15 find_ticket_by_ext_id "${EXT_ID_A}"; then
  pass "${SCENARIO}: ticket A found id=${TICKET_ID}"
else
  fail "${SCENARIO}: ticket A not found after 15s"
  _fail=$((_fail + 1))
  exit 1
fi

TICKET_A="${TICKET_ID}"

info "${SCENARIO}: solve ticket A"
http POST "/api/desk/tickets/${TICKET_A}/solve"
_check "solve -> 200/204" expect_ok

# 3. GET /api/desk/reports/summary
info "${SCENARIO}: GET /api/desk/reports/summary"
http GET "/api/desk/reports/summary"
_check "summary -> 200" expect_status 200

# 4. Validate top-level keys and values using jq_extract
open_tickets=$(jq_extract '.openTickets')
solved_today=$(jq_extract '.solvedToday')
sla_rate=$(jq_extract '.slaBreachRatePct')
created_len=$(jq_extract '.createdSeries | length')
solved_len=$(jq_extract '.solvedSeries | length')
by_channel_type=$(jq_extract '.byChannel | type')
by_agent_type=$(jq_extract '.byAgent | type')

_check "openTickets key present" [ "${open_tickets}" != "null" ]
_check "solvedToday key present" [ "${solved_today}" != "null" ]
_check "slaBreachRatePct key present" [ "${sla_rate}" != "null" ]

_check "openTickets >= 1" [ "${open_tickets:-0}" -ge 1 ]
_check "solvedToday >= 1" [ "${solved_today:-0}" -ge 1 ]

_check "createdSeries has 14 entries" [ "${created_len}" = "14" ]
_check "solvedSeries has 14 entries" [ "${solved_len}" = "14" ]

_check "byChannel is array" [ "${by_channel_type}" = "array" ]
_check "byAgent is array" [ "${by_agent_type}" = "array" ]

[ "$_fail" -eq 0 ]
