#!/usr/bin/env bash
# 24_bulk_actions.sh — Bulk operations on multiple tickets
set -euo pipefail

SCENARIO="24_bulk_actions"
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
  fail "${SCENARIO}: no webhook token (run 03_channel_setup first)"
  exit 1
fi

TS="$(date +%s)"
EXT_1="e2e-bulk-1-${TS}"
EXT_2="e2e-bulk-2-${TS}"
EXT_3="e2e-bulk-3-${TS}"

# ── 1. Create 3 tickets via webhook ──────────────────────────────────────────
info "${SCENARIO}: creating 3 tickets"

for EXT_ID in "$EXT_1" "$EXT_2" "$EXT_3"; do
  http_unauth POST "/v2/webhook/CUSTOM/${E2E_TEST_WEBHOOK_TOKEN}" \
    "{\"externalTicketId\":\"${EXT_ID}\",\"customerIdentifier\":\"bulk-$(random_token)@e2e.test\",\"message\":\"Bulk test message\",\"eventType\":\"NEW_TICKET\"}"
  _check "webhook ${EXT_ID} → 2xx" expect_2xx
done

# Poll until all 3 are findable
T1_ID=""
T2_ID=""
T3_ID=""

poll_until 20 find_ticket_by_ext_id "$EXT_1" || { fail "${SCENARIO}: ticket 1 not created"; exit 1; }
T1_ID="$TICKET_ID"

poll_until 20 find_ticket_by_ext_id "$EXT_2" || { fail "${SCENARIO}: ticket 2 not created"; exit 1; }
T2_ID="$TICKET_ID"

poll_until 20 find_ticket_by_ext_id "$EXT_3" || { fail "${SCENARIO}: ticket 3 not created"; exit 1; }
T3_ID="$TICKET_ID"

pass "${SCENARIO}: 3 tickets created: ${T1_ID}, ${T2_ID}, ${T3_ID}"

# ── 2. Bulk SOLVE ─────────────────────────────────────────────────────────────
info "${SCENARIO}: bulk SOLVE all 3 tickets"
http POST "/api/desk/tickets/bulk" \
  "{\"ticketIds\":[\"${T1_ID}\",\"${T2_ID}\",\"${T3_ID}\"],\"action\":\"SOLVE\",\"payload\":{}}"
_check "bulk SOLVE → 200" expect_ok

SUCCESS_COUNT=$(jq_extract '.successCount')
_check "successCount == 3" [ "${SUCCESS_COUNT}" = "3" ]

FAILURES=$(jq_extract '.failures | length')
_check "failures empty" [ "${FAILURES}" = "0" ]

# Verify each ticket is now SOLVED
for TID in "$T1_ID" "$T2_ID" "$T3_ID"; do
  http GET "/api/desk/tickets/${TID}"
  STATUS=$(jq_extract '.status')
  _check "ticket ${TID} status=SOLVED" [ "${STATUS}" = "SOLVED" ]
done

# ── 3. Bulk ADD_TAG ───────────────────────────────────────────────────────────
# Need to create fresh tickets (solved ones can't get tags changed in some states)
info "${SCENARIO}: creating 2 more tickets for tag test"
EXT_4="e2e-bulk-4-${TS}"
EXT_5="e2e-bulk-5-${TS}"
for EXT_ID in "$EXT_4" "$EXT_5"; do
  http_unauth POST "/v2/webhook/CUSTOM/${E2E_TEST_WEBHOOK_TOKEN}" \
    "{\"externalTicketId\":\"${EXT_ID}\",\"customerIdentifier\":\"bulk2-$(random_token)@e2e.test\",\"message\":\"Tag test\",\"eventType\":\"NEW_TICKET\"}"
  _check "webhook ${EXT_ID} → 2xx" expect_2xx
done

T4_ID=""
T5_ID=""
poll_until 20 find_ticket_by_ext_id "$EXT_4" || { fail "${SCENARIO}: ticket 4 not created"; exit 1; }
T4_ID="$TICKET_ID"
poll_until 20 find_ticket_by_ext_id "$EXT_5" || { fail "${SCENARIO}: ticket 5 not created"; exit 1; }
T5_ID="$TICKET_ID"

info "${SCENARIO}: bulk ADD_TAG urgent to ${T4_ID}, ${T5_ID}"
http POST "/api/desk/tickets/bulk" \
  "{\"ticketIds\":[\"${T4_ID}\",\"${T5_ID}\"],\"action\":\"ADD_TAG\",\"payload\":{\"tag\":\"urgent\"}}"
_check "bulk ADD_TAG → 200" expect_ok

SUCCESS_COUNT=$(jq_extract '.successCount')
_check "ADD_TAG successCount == 2" [ "${SUCCESS_COUNT}" = "2" ]

# Verify tags on both tickets
for TID in "$T4_ID" "$T5_ID"; do
  http GET "/api/desk/tickets/${TID}"
  HAS_TAG=$(jq_extract '.tags | map(select(. == "urgent")) | length')
  _check "ticket ${TID} has tag=urgent" [ "${HAS_TAG}" = "1" ]
done

# ── 4. Bulk SOLVE with one bogus ID ───────────────────────────────────────────
info "${SCENARIO}: bulk SOLVE with 1 valid + 1 bogus ticket ID"
http POST "/api/desk/tickets/bulk" \
  "{\"ticketIds\":[\"${T4_ID}\",\"does-not-exist-9999-${TS}\"],\"action\":\"SOLVE\",\"payload\":{}}"
_check "partial bulk SOLVE → 200" expect_ok

SUCCESS_COUNT=$(jq_extract '.successCount')
FAILURES=$(jq_extract '.failures | length')
_check "partial SOLVE successCount == 1" [ "${SUCCESS_COUNT}" = "1" ]
_check "partial SOLVE failures == 1" [ "${FAILURES}" = "1" ]

FAILED_ID=$(jq_extract '.failures[0].ticketId')
_check "failure is the bogus ticket ID" [ "${FAILED_ID}" = "does-not-exist-9999-${TS}" ]

# ── 5. Empty ticketIds → 400 ──────────────────────────────────────────────────
info "${SCENARIO}: empty ticketIds should return 400"
http POST "/api/desk/tickets/bulk" \
  '{"ticketIds":[],"action":"SOLVE","payload":{}}'
_check "empty ticketIds → 400" expect_status 400

info "${SCENARIO}: fail=${_fail}"
[ "$_fail" -eq 0 ]
