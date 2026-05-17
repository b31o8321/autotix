#!/usr/bin/env bash
# 29_telegram_channel.sh — Telegram channel end-to-end test
#
# Tests:
#   1. Create TELEGRAM channel via REST with fake bot_token
#   2. GET /channels — verify TELEGRAM channel appears
#   3. POST /v2/webhook/TELEGRAM/{token} with a sample Update body, no secret → ticket created
#   4. Set webhook secret_token; POST without X-Telegram-Bot-Api-Secret-Token → rejected
#   5. POST with correct X-Telegram-Bot-Api-Secret-Token header → accepted, ticket created
set -euo pipefail

SCENARIO="29_telegram_channel"
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

# ── 1. Create TELEGRAM channel ─────────────────────────────────────────────────
CHANNEL_NAME="E2E-Telegram-$(random_token)"
info "${SCENARIO}: create TELEGRAM channel '${CHANNEL_NAME}'"
http POST /api/admin/channels/connect-api-key \
  "{\"platform\":\"TELEGRAM\",\"channelType\":\"CHAT\",\"displayName\":\"${CHANNEL_NAME}\",\"credentials\":{\"bot_token\":\"111111111:AAFakeTokenForE2ETesting\"}}"
_check "create TELEGRAM channel → 200" expect_status 200

TG_CHANNEL_ID=$(jq_extract '.channelId')
if [ -z "$TG_CHANNEL_ID" ] || [ "$TG_CHANNEL_ID" = "null" ]; then
  fail "${SCENARIO}: no channelId returned — aborting"
  exit 1
fi
pass "${SCENARIO}: TELEGRAM channel created id=${TG_CHANNEL_ID}"

# ── 2. Verify channel appears in list ─────────────────────────────────────────
info "${SCENARIO}: GET /api/admin/channels"
http GET /api/admin/channels
_check "list channels → 200" expect_status 200

found=$(jq_extract "[.[] | select(.id == \"${TG_CHANNEL_ID}\")] | length")
_check "channel appears in list" assert_eq "found" "$found" "1"

platform=$(jq_extract "[.[] | select(.id == \"${TG_CHANNEL_ID}\")] | .[0].platform")
_check "platform=TELEGRAM" assert_eq "platform" "$platform" "TELEGRAM"

TG_WEBHOOK_TOKEN=$(jq_extract "[.[] | select(.id == \"${TG_CHANNEL_ID}\")] | .[0].webhookToken")
if [ -z "$TG_WEBHOOK_TOKEN" ] || [ "$TG_WEBHOOK_TOKEN" = "null" ]; then
  fail "${SCENARIO}: no webhookToken — aborting"
  exit 1
fi
pass "${SCENARIO}: webhookToken acquired: ${TG_WEBHOOK_TOKEN:0:8}..."

# ── 3. POST webhook (valid Update, no secret → accepted) ──────────────────────
CHAT_ID="e2e-$(random_token | tr -dc 0-9 | head -c 9)"
if [ -z "$CHAT_ID" ]; then CHAT_ID="987654321"; fi
CUSTOMER_USERNAME="e2e_user_$(random_token | head -c 8)"

TG_UPDATE_BODY="{
  \"update_id\": 100001,
  \"message\": {
    \"message_id\": 1,
    \"from\": {\"id\": ${CHAT_ID}, \"username\": \"${CUSTOMER_USERNAME}\", \"first_name\": \"E2E\"},
    \"chat\": {\"id\": ${CHAT_ID}, \"type\": \"private\"},
    \"text\": \"Hello from E2E test — need help please\",
    \"date\": 1700000000
  }
}"

info "${SCENARIO}: POST Telegram Update (no secret) — expect accepted"
RAW_RESP=$(curl -s -w "\n__STATUS__:%{http_code}" -X POST \
  "${BASE_URL}/v2/webhook/TELEGRAM/${TG_WEBHOOK_TOKEN}" \
  -H "Content-Type: application/json" \
  --data-raw "$TG_UPDATE_BODY" 2>/dev/null)
LAST_HTTP_STATUS=$(printf '%s' "$RAW_RESP" | grep -o '__STATUS__:[0-9]*' | cut -d: -f2)
LAST_HTTP_BODY=$(printf '%s' "$RAW_RESP" | sed 's/__STATUS__:[0-9]*$//')

_check "Telegram Update (no secret) → 2xx" expect_2xx

# ── 4. Poll for ticket ─────────────────────────────────────────────────────────
info "${SCENARIO}: polling for ticket from Telegram webhook (up to 15s)"
TICKET_ID=""
if poll_until 15 find_ticket_by_ext_id "${CHAT_ID}"; then
  pass "${SCENARIO}: ticket found id=${TICKET_ID}"
else
  fail "${SCENARIO}: ticket not found after 15s"
  _fail=$((_fail + 1))
fi

if [ -n "${TICKET_ID:-}" ] && [ "${TICKET_ID}" != "null" ]; then
  http GET "/api/desk/tickets/${TICKET_ID}"
  _check "ticket detail → 200" expect_status 200
  subject=$(jq_extract '.subject')
  _check "subject contains message text" assert_contains "subject" "$subject" "Hello from E2E"
fi

# ── 5. Set webhook secret ──────────────────────────────────────────────────────
TG_SECRET="e2e-tg-secret-$(random_token)"
info "${SCENARIO}: set secret_token"
http PUT "/api/admin/channels/${TG_CHANNEL_ID}/secret" \
  "{\"secret\":\"${TG_SECRET}\"}"
_check "set secret → 200 or 204" expect_ok

# ── 6. POST without secret header → expect 4xx ───────────────────────────────
CHAT_ID2="e2e-$(random_token | tr -dc 0-9 | head -c 9)"
if [ -z "$CHAT_ID2" ]; then CHAT_ID2="111222333"; fi

TG_BODY_NO_SECRET="{
  \"update_id\": 100002,
  \"message\": {
    \"from\": {\"id\": ${CHAT_ID2}, \"username\": \"bad_user\"},
    \"chat\": {\"id\": ${CHAT_ID2}},
    \"text\": \"Should be rejected\"
  }
}"

info "${SCENARIO}: POST Telegram Update without secret header — expect rejection"
RAW_RESP2=$(curl -s -w "\n__STATUS__:%{http_code}" -X POST \
  "${BASE_URL}/v2/webhook/TELEGRAM/${TG_WEBHOOK_TOKEN}" \
  -H "Content-Type: application/json" \
  --data-raw "$TG_BODY_NO_SECRET" 2>/dev/null)
LAST_HTTP_STATUS=$(printf '%s' "$RAW_RESP2" | grep -o '__STATUS__:[0-9]*' | cut -d: -f2)
LAST_HTTP_BODY=$(printf '%s' "$RAW_RESP2" | sed 's/__STATUS__:[0-9]*$//')

if [ "${LAST_HTTP_STATUS}" = "401" ] || [ "${LAST_HTTP_STATUS}" = "403" ] || [ "${LAST_HTTP_STATUS}" = "400" ]; then
  pass "${SCENARIO}: missing secret correctly rejected (HTTP ${LAST_HTTP_STATUS})"
else
  fail "${SCENARIO}: missing secret should be rejected; got HTTP ${LAST_HTTP_STATUS}"
  _fail=$((_fail + 1))
fi

# ── 7. POST with correct secret header → accepted ─────────────────────────────
CHAT_ID3="e2e-$(random_token | tr -dc 0-9 | head -c 9)"
if [ -z "$CHAT_ID3" ]; then CHAT_ID3="444555666"; fi
CUSTOMER_USERNAME3="e2e_good_$(random_token | head -c 8)"

TG_BODY_WITH_SECRET="{
  \"update_id\": 100003,
  \"message\": {
    \"from\": {\"id\": ${CHAT_ID3}, \"username\": \"${CUSTOMER_USERNAME3}\"},
    \"chat\": {\"id\": ${CHAT_ID3}},
    \"text\": \"Authenticated E2E Telegram message\"
  }
}"

info "${SCENARIO}: POST Telegram Update with correct X-Telegram-Bot-Api-Secret-Token"
RAW_RESP3=$(curl -s -w "\n__STATUS__:%{http_code}" -X POST \
  "${BASE_URL}/v2/webhook/TELEGRAM/${TG_WEBHOOK_TOKEN}" \
  -H "Content-Type: application/json" \
  -H "X-Telegram-Bot-Api-Secret-Token: ${TG_SECRET}" \
  --data-raw "$TG_BODY_WITH_SECRET" 2>/dev/null)
LAST_HTTP_STATUS=$(printf '%s' "$RAW_RESP3" | grep -o '__STATUS__:[0-9]*' | cut -d: -f2)
LAST_HTTP_BODY=$(printf '%s' "$RAW_RESP3" | sed 's/__STATUS__:[0-9]*$//')

_check "correct secret → 2xx" expect_2xx

# Poll for second ticket
info "${SCENARIO}: polling for second ticket (correct-secret webhook, up to 15s)"
TICKET_ID=""
if poll_until 15 find_ticket_by_ext_id "${CHAT_ID3}"; then
  pass "${SCENARIO}: second ticket found id=${TICKET_ID}"
else
  fail "${SCENARIO}: second ticket not found after 15s"
  _fail=$((_fail + 1))
fi

info "${SCENARIO}: done. channel_id=${TG_CHANNEL_ID}"
[ "$_fail" -eq 0 ]
