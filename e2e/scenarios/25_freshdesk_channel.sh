#!/usr/bin/env bash
# 25_freshdesk_channel.sh — Freshdesk channel end-to-end test
#
# Tests:
#   1. Create FRESHDESK channel via REST
#   2. GET /channels — verify FRESHDESK channel appears
#   3. POST webhook (nested freshdesk_webhook shape, no secret) → ticket created
#   4. Set webhook token; post missing X-Autotix-Webhook-Token → rejected
#   5. POST webhook with correct token → accepted, second ticket created
set -euo pipefail

SCENARIO="25_freshdesk_channel"
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

# ── 1. Create FRESHDESK channel ───────────────────────────────────────────────
CHANNEL_NAME="E2E-Freshdesk-$(random_token)"
info "${SCENARIO}: create FRESHDESK channel '${CHANNEL_NAME}'"
http POST /api/admin/channels/connect-api-key \
  "{\"platform\":\"FRESHDESK\",\"channelType\":\"EMAIL\",\"displayName\":\"${CHANNEL_NAME}\",\"credentials\":{\"domain\":\"e2e-test\",\"api_key\":\"e2eApiKey000\"}}"
_check "create FRESHDESK channel → 200" expect_status 200

FD_CHANNEL_ID=$(jq_extract '.channelId')
if [ -z "$FD_CHANNEL_ID" ] || [ "$FD_CHANNEL_ID" = "null" ]; then
  fail "${SCENARIO}: no channelId returned — aborting"
  exit 1
fi
pass "${SCENARIO}: FRESHDESK channel created id=${FD_CHANNEL_ID}"

# ── 2. Verify channel appears in list ─────────────────────────────────────────
info "${SCENARIO}: GET /api/admin/channels"
http GET /api/admin/channels
_check "list channels → 200" expect_status 200

found=$(jq_extract "[.[] | select(.id == \"${FD_CHANNEL_ID}\")] | length")
_check "channel appears in list" assert_eq "found" "$found" "1"

platform=$(jq_extract "[.[] | select(.id == \"${FD_CHANNEL_ID}\")] | .[0].platform")
_check "platform=FRESHDESK" assert_eq "platform" "$platform" "FRESHDESK"

FD_WEBHOOK_TOKEN=$(jq_extract "[.[] | select(.id == \"${FD_CHANNEL_ID}\")] | .[0].webhookToken")
if [ -z "$FD_WEBHOOK_TOKEN" ] || [ "$FD_WEBHOOK_TOKEN" = "null" ]; then
  fail "${SCENARIO}: no webhookToken — aborting"
  exit 1
fi
pass "${SCENARIO}: webhookToken acquired: ${FD_WEBHOOK_TOKEN:0:8}..."

# ── 3. POST webhook (nested freshdesk_webhook shape, no secret → accepted) ────
EXT_TICKET_ID="e2e-25-ticket-$(random_token)"
CUSTOMER_EMAIL="e2e-fd-$(random_token)@example.test"

FD_NESTED_BODY="{
  \"freshdesk_webhook\": {
    \"ticket_id\": \"${EXT_TICKET_ID}\",
    \"ticket_subject\": \"E2E Freshdesk test ticket\",
    \"ticket_description\": \"This is an automated e2e test.\",
    \"ticket_status\": \"Open\",
    \"ticket_priority\": \"Medium\",
    \"ticket_requester_email\": \"${CUSTOMER_EMAIL}\",
    \"ticket_requester_name\": \"E2E TestUser\",
    \"ticket_url\": \"https://e2e-test.freshdesk.com/a/tickets/1\",
    \"triggered_event\": \"ticket_created\"
  }
}"

info "${SCENARIO}: POST freshdesk_webhook (nested, no token) — expect accepted"
RAW_RESP=$(curl -s -w "\n__STATUS__:%{http_code}" -X POST \
  "${BASE_URL}/v2/webhook/FRESHDESK/${FD_WEBHOOK_TOKEN}" \
  -H "Content-Type: application/json" \
  --data-raw "$FD_NESTED_BODY" 2>/dev/null)
LAST_HTTP_STATUS=$(printf '%s' "$RAW_RESP" | grep -o '__STATUS__:[0-9]*' | cut -d: -f2)
LAST_HTTP_BODY=$(printf '%s' "$RAW_RESP" | sed 's/__STATUS__:[0-9]*$//')

_check "nested webhook (no secret) → 2xx" expect_2xx

# ── 4. Poll for ticket ─────────────────────────────────────────────────────────
info "${SCENARIO}: polling for ticket from nested webhook (up to 15s)"
TICKET_ID=""
if poll_until 15 find_ticket_by_ext_id "${EXT_TICKET_ID}"; then
  pass "${SCENARIO}: ticket found id=${TICKET_ID}"
else
  fail "${SCENARIO}: ticket not found after 15s"
  _fail=$((_fail + 1))
fi

if [ -n "${TICKET_ID:-}" ] && [ "${TICKET_ID}" != "null" ]; then
  http GET "/api/desk/tickets/${TICKET_ID}"
  _check "ticket detail → 200" expect_status 200
  subject=$(jq_extract '.subject')
  _check "subject matches" assert_contains "subject" "$subject" "E2E Freshdesk"
fi

# ── 5. Set webhook token ──────────────────────────────────────────────────────
FD_SECRET="e2e-fd-secret-$(random_token)"
info "${SCENARIO}: set webhook_secret (token)"
http PUT "/api/admin/channels/${FD_CHANNEL_ID}/secret" \
  "{\"secret\":\"${FD_SECRET}\"}"
_check "set secret → 200 or 204" expect_ok

# ── 6. POST webhook missing X-Autotix-Webhook-Token → expect 4xx ──────────────
EXT_TICKET_ID2="e2e-25-no-token-$(random_token)"
FD_BODY2="{\"freshdesk_webhook\":{\"ticket_id\":\"${EXT_TICKET_ID2}\",\"ticket_subject\":\"Should be rejected\",\"ticket_description\":\"\",\"ticket_requester_email\":\"bad@test.com\",\"ticket_requester_name\":\"Bad\",\"triggered_event\":\"ticket_created\",\"ticket_status\":\"Open\"}}"

info "${SCENARIO}: POST webhook without token — expect rejection"
RAW_RESP2=$(curl -s -w "\n__STATUS__:%{http_code}" -X POST \
  "${BASE_URL}/v2/webhook/FRESHDESK/${FD_WEBHOOK_TOKEN}" \
  -H "Content-Type: application/json" \
  --data-raw "$FD_BODY2" 2>/dev/null)
LAST_HTTP_STATUS=$(printf '%s' "$RAW_RESP2" | grep -o '__STATUS__:[0-9]*' | cut -d: -f2)
LAST_HTTP_BODY=$(printf '%s' "$RAW_RESP2" | sed 's/__STATUS__:[0-9]*$//')

if [ "${LAST_HTTP_STATUS}" = "401" ] || [ "${LAST_HTTP_STATUS}" = "403" ] || [ "${LAST_HTTP_STATUS}" = "400" ]; then
  pass "${SCENARIO}: missing token correctly rejected (HTTP ${LAST_HTTP_STATUS})"
else
  fail "${SCENARIO}: missing token should be rejected; got HTTP ${LAST_HTTP_STATUS}"
  _fail=$((_fail + 1))
fi

# ── 7. POST webhook with correct token → accepted ─────────────────────────────
EXT_TICKET_ID3="e2e-25-good-$(random_token)"
CUSTOMER_EMAIL3="e2e-fd-good-$(random_token)@example.test"
FD_BODY3="{\"freshdesk_webhook\":{\"ticket_id\":\"${EXT_TICKET_ID3}\",\"ticket_subject\":\"Authenticated E2E ticket\",\"ticket_description\":\"Token was correct.\",\"ticket_requester_email\":\"${CUSTOMER_EMAIL3}\",\"ticket_requester_name\":\"GoodUser\",\"triggered_event\":\"ticket_created\",\"ticket_status\":\"Open\"}}"

info "${SCENARIO}: POST webhook with correct X-Autotix-Webhook-Token"
RAW_RESP3=$(curl -s -w "\n__STATUS__:%{http_code}" -X POST \
  "${BASE_URL}/v2/webhook/FRESHDESK/${FD_WEBHOOK_TOKEN}" \
  -H "Content-Type: application/json" \
  -H "X-Autotix-Webhook-Token: ${FD_SECRET}" \
  --data-raw "$FD_BODY3" 2>/dev/null)
LAST_HTTP_STATUS=$(printf '%s' "$RAW_RESP3" | grep -o '__STATUS__:[0-9]*' | cut -d: -f2)
LAST_HTTP_BODY=$(printf '%s' "$RAW_RESP3" | sed 's/__STATUS__:[0-9]*$//')

_check "correct token webhook → 2xx" expect_2xx

# Poll for second ticket
info "${SCENARIO}: polling for second ticket (correct-token webhook, up to 15s)"
TICKET_ID=""
if poll_until 15 find_ticket_by_ext_id "${EXT_TICKET_ID3}"; then
  pass "${SCENARIO}: second ticket found id=${TICKET_ID}"
else
  fail "${SCENARIO}: second ticket not found after 15s"
  _fail=$((_fail + 1))
fi

info "${SCENARIO}: done. channel_id=${FD_CHANNEL_ID}"
[ "$_fail" -eq 0 ]
