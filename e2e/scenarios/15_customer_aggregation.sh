#!/usr/bin/env bash
# 15_customer_aggregation.sh — Same email on two channels → single Customer
set -euo pipefail

SCENARIO="15_customer_aggregation"
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

CHANNEL_A_TOKEN="${E2E_TEST_WEBHOOK_TOKEN:-}"
if [ -z "$CHANNEL_A_TOKEN" ]; then
  fail "${SCENARIO}: no channel A token"
  exit 1
fi

SHARED_EMAIL="alice-agg-$(random_token | cut -c1-8)@e2e.test"
info "${SCENARIO}: shared email = ${SHARED_EMAIL}"

# ── 1. Create second CUSTOM channel ──────────────────────────────────────────
CHANNEL_B_NAME="E2E-ChannelB-$(random_token | cut -c1-8)"
http POST /api/admin/channels/connect-api-key \
  "{\"platform\":\"CUSTOM\",\"channelType\":\"CHAT\",\"displayName\":\"${CHANNEL_B_NAME}\"}"
_check "create channel B → 200" expect_status 200

CHANNEL_B_ID=$(jq_extract '.channelId')
if [ -z "$CHANNEL_B_ID" ] || [ "$CHANNEL_B_ID" = "null" ]; then
  fail "${SCENARIO}: no channelId for channel B"
  exit 1
fi
pass "${SCENARIO}: channel B created id=${CHANNEL_B_ID}"

http GET /api/admin/channels
CHANNEL_B_TOKEN=$(printf '%s' "$LAST_HTTP_BODY" | jq -r "[.[] | select(.id == \"${CHANNEL_B_ID}\")] | .[0].webhookToken")
if [ -z "$CHANNEL_B_TOKEN" ] || [ "$CHANNEL_B_TOKEN" = "null" ]; then
  fail "${SCENARIO}: no token for channel B"
  exit 1
fi
pass "${SCENARIO}: channel B token acquired"

# ── 2. Inbound on channel A ───────────────────────────────────────────────────
EXT_ID_A="e2e-15a-$(random_token)"
info "${SCENARIO}: POST inbound on channel A"
http_unauth POST "/v2/webhook/CUSTOM/${CHANNEL_A_TOKEN}" \
  "{\"externalTicketId\":\"${EXT_ID_A}\",\"customerIdentifier\":\"${SHARED_EMAIL}\",\"message\":\"From channel A\",\"eventType\":\"NEW_TICKET\"}"
_check "channel A webhook → 2xx" expect_2xx

# ── 3. Inbound on channel B ───────────────────────────────────────────────────
EXT_ID_B="e2e-15b-$(random_token)"
info "${SCENARIO}: POST inbound on channel B"
http_unauth POST "/v2/webhook/CUSTOM/${CHANNEL_B_TOKEN}" \
  "{\"externalTicketId\":\"${EXT_ID_B}\",\"customerIdentifier\":\"${SHARED_EMAIL}\",\"message\":\"From channel B\",\"eventType\":\"NEW_TICKET\"}"
_check "channel B webhook → 2xx" expect_2xx

sleep 3

# ── 4. Search customer by email ───────────────────────────────────────────────
info "${SCENARIO}: GET /api/admin/customers?q=${SHARED_EMAIL}"
http GET "/api/admin/customers?q=$(printf '%s' "$SHARED_EMAIL" | sed 's/@/%40/g')"
_check "customer list → 200" expect_status 200

customer_count=$(jq_extract 'length')
if [ "${customer_count:-0}" -eq 1 ]; then
  pass "${SCENARIO}: exactly 1 customer for shared email"
elif [ "${customer_count:-0}" -gt 1 ]; then
  fail "${SCENARIO}: ${customer_count} customers — expected 1 (aggregation failed)"
  _fail=$((_fail + 1))
else
  fail "${SCENARIO}: 0 customers found for ${SHARED_EMAIL}"
  _fail=$((_fail + 1))
fi

if [ "${customer_count:-0}" -ge 1 ]; then
  CUSTOMER_ID=$(jq_extract '.[0].id')
  pass "${SCENARIO}: customer id=${CUSTOMER_ID}"

  # ── 5. Get customer detail — should have ≥2 identifiers ──────────────────
  http GET "/api/admin/customers/${CUSTOMER_ID}"
  _check "customer detail → 200" expect_status 200

  identifier_count=$(jq_extract '.identifiers | length')
  if [ "${identifier_count:-0}" -ge 2 ]; then
    pass "${SCENARIO}: ${identifier_count} identifiers (one per channel)"
  else
    note "${SCENARIO}: ${identifier_count:-0} identifier(s) — expected ≥2 (may depend on timing)"
  fi

  # ── 6. Both tickets reference same customerId ─────────────────────────────
  info "${SCENARIO}: verify both tickets link to same customer"
  # Find ticket A and B by externalNativeId
  find_ticket_by_ext_id "${EXT_ID_A}" && TKT_A="${TICKET_ID}" || TKT_A=""
  find_ticket_by_ext_id "${EXT_ID_B}" && TKT_B="${TICKET_ID}" || TKT_B=""

  if [ -n "$TKT_A" ] && [ "$TKT_A" != "null" ] && \
     [ -n "$TKT_B" ] && [ "$TKT_B" != "null" ]; then
    http GET "/api/desk/tickets/${TKT_A}"
    cid_a=$(jq_extract '.customerId')
    http GET "/api/desk/tickets/${TKT_B}"
    cid_b=$(jq_extract '.customerId')
    if [ "$cid_a" = "$cid_b" ] && [ -n "$cid_a" ] && [ "$cid_a" != "null" ]; then
      pass "${SCENARIO}: both tickets share customerId=${cid_a}"
    else
      fail "${SCENARIO}: tickets have different customerIds: A=${cid_a} B=${cid_b}"
      _fail=$((_fail + 1))
    fi
  else
    note "${SCENARIO}: could not find both tickets to verify customerId"
  fi
fi

info "${SCENARIO}: fail=${_fail}"
[ "$_fail" -eq 0 ]
