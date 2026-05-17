#!/usr/bin/env bash
# 28_line_channel.sh — LINE Messaging API channel end-to-end test
#
# Tests:
#   1. Create LINE channel via REST with fake credentials
#   2. GET /channels — verify LINE channel appears
#   3. POST webhook without X-Line-Signature → rejected (401)
#   4. POST webhook with bad HMAC → rejected (401)
#   5. POST webhook with correct HMAC → ticket created with line:U... customer identifier
set -euo pipefail

SCENARIO="28_line_channel"
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

# ── 1. Create LINE channel ────────────────────────────────────────────────────
CHANNEL_NAME="E2E-LINE-$(random_token)"
LINE_SECRET="test-secret"
info "${SCENARIO}: create LINE channel '${CHANNEL_NAME}'"
http POST /api/admin/channels/connect-api-key \
  "{\"platform\":\"LINE\",\"channelType\":\"CHAT\",\"displayName\":\"${CHANNEL_NAME}\",\"credentials\":{\"channel_access_token\":\"e2e-fake-access-token\",\"channel_secret\":\"${LINE_SECRET}\"}}"
_check "create LINE channel → 200" expect_status 200

LINE_CHANNEL_ID=$(jq_extract '.channelId')
if [ -z "$LINE_CHANNEL_ID" ] || [ "$LINE_CHANNEL_ID" = "null" ]; then
  fail "${SCENARIO}: no channelId returned — aborting"
  exit 1
fi
pass "${SCENARIO}: LINE channel created id=${LINE_CHANNEL_ID}"

# ── 2. Verify channel appears in list ─────────────────────────────────────────
info "${SCENARIO}: GET /api/admin/channels"
http GET /api/admin/channels
_check "list channels → 200" expect_status 200

found=$(jq_extract "[.[] | select(.id == \"${LINE_CHANNEL_ID}\")] | length")
_check "channel appears in list" assert_eq "found" "$found" "1"

platform=$(jq_extract "[.[] | select(.id == \"${LINE_CHANNEL_ID}\")] | .[0].platform")
_check "platform=LINE" assert_eq "platform" "$platform" "LINE"

LINE_WEBHOOK_TOKEN=$(jq_extract "[.[] | select(.id == \"${LINE_CHANNEL_ID}\")] | .[0].webhookToken")
if [ -z "$LINE_WEBHOOK_TOKEN" ] || [ "$LINE_WEBHOOK_TOKEN" = "null" ]; then
  fail "${SCENARIO}: no webhookToken — aborting"
  exit 1
fi
pass "${SCENARIO}: webhookToken acquired: ${LINE_WEBHOOK_TOKEN:0:8}..."

# ── Sample payload ─────────────────────────────────────────────────────────────
LINE_USER_ID="U$(random_token)"
LINE_BODY="{\"events\":[{\"type\":\"message\",\"message\":{\"type\":\"text\",\"text\":\"Hello from LINE e2e\"},\"source\":{\"userId\":\"${LINE_USER_ID}\"},\"replyToken\":\"replytoken\",\"timestamp\":1700000000000}]}"

# ── 3. POST webhook without X-Line-Signature → rejected ───────────────────────
info "${SCENARIO}: POST webhook without X-Line-Signature — expect 401"
RAW_RESP_NO_SIG=$(curl -s -w "\n__STATUS__:%{http_code}" -X POST \
  "${BASE_URL}/v2/webhook/LINE/${LINE_WEBHOOK_TOKEN}" \
  -H "Content-Type: application/json" \
  --data-raw "$LINE_BODY" 2>/dev/null)
LAST_HTTP_STATUS=$(printf '%s' "$RAW_RESP_NO_SIG" | grep -o '__STATUS__:[0-9]*' | cut -d: -f2)
LAST_HTTP_BODY=$(printf '%s' "$RAW_RESP_NO_SIG" | sed 's/__STATUS__:[0-9]*$//')

if [ "${LAST_HTTP_STATUS}" = "401" ] || [ "${LAST_HTTP_STATUS}" = "403" ] || [ "${LAST_HTTP_STATUS}" = "400" ]; then
  pass "${SCENARIO}: missing signature correctly rejected (HTTP ${LAST_HTTP_STATUS})"
else
  fail "${SCENARIO}: missing signature should be rejected; got HTTP ${LAST_HTTP_STATUS}"
  _fail=$((_fail + 1))
fi

# ── 4. POST webhook with bad HMAC → rejected ──────────────────────────────────
info "${SCENARIO}: POST webhook with bad HMAC — expect 401"
RAW_RESP_BAD=$(curl -s -w "\n__STATUS__:%{http_code}" -X POST \
  "${BASE_URL}/v2/webhook/LINE/${LINE_WEBHOOK_TOKEN}" \
  -H "Content-Type: application/json" \
  -H "X-Line-Signature: aW52YWxpZHNpZ25hdHVyZQ==" \
  --data-raw "$LINE_BODY" 2>/dev/null)
LAST_HTTP_STATUS=$(printf '%s' "$RAW_RESP_BAD" | grep -o '__STATUS__:[0-9]*' | cut -d: -f2)
LAST_HTTP_BODY=$(printf '%s' "$RAW_RESP_BAD" | sed 's/__STATUS__:[0-9]*$//')

if [ "${LAST_HTTP_STATUS}" = "401" ] || [ "${LAST_HTTP_STATUS}" = "403" ] || [ "${LAST_HTTP_STATUS}" = "400" ]; then
  pass "${SCENARIO}: bad HMAC correctly rejected (HTTP ${LAST_HTTP_STATUS})"
else
  fail "${SCENARIO}: bad HMAC should be rejected; got HTTP ${LAST_HTTP_STATUS}"
  _fail=$((_fail + 1))
fi

# ── 5. POST webhook with correct HMAC → ticket created ────────────────────────
if command -v openssl >/dev/null 2>&1; then
  CORRECT_SIG=$(printf '%s' "${LINE_BODY}" | openssl dgst -sha256 -hmac "${LINE_SECRET}" -binary | base64)

  info "${SCENARIO}: POST webhook with correct HMAC"
  RAW_RESP_GOOD=$(curl -s -w "\n__STATUS__:%{http_code}" -X POST \
    "${BASE_URL}/v2/webhook/LINE/${LINE_WEBHOOK_TOKEN}" \
    -H "Content-Type: application/json" \
    -H "X-Line-Signature: ${CORRECT_SIG}" \
    --data-raw "$LINE_BODY" 2>/dev/null)
  LAST_HTTP_STATUS=$(printf '%s' "$RAW_RESP_GOOD" | grep -o '__STATUS__:[0-9]*' | cut -d: -f2)
  LAST_HTTP_BODY=$(printf '%s' "$RAW_RESP_GOOD" | sed 's/__STATUS__:[0-9]*$//')

  _check "correct HMAC webhook → 2xx" expect_2xx

  # Poll for ticket with externalNativeId = LINE_USER_ID
  info "${SCENARIO}: polling for ticket from LINE userId=${LINE_USER_ID} (up to 15s)"
  TICKET_ID=""
  if poll_until 15 find_ticket_by_ext_id "${LINE_USER_ID}"; then
    pass "${SCENARIO}: ticket found id=${TICKET_ID}"

    # Verify customerIdentifier contains line: prefix
    http GET "/api/admin/tickets/${TICKET_ID}"
    _check "GET ticket → 200" expect_status 200
    customer_id=$(jq_extract '.customerIdentifier // .customer // ""')
    if printf '%s' "$customer_id" | grep -q "^line:"; then
      pass "${SCENARIO}: customerIdentifier has 'line:' prefix: ${customer_id}"
    else
      fail "${SCENARIO}: expected customerIdentifier starting with 'line:'; got '${customer_id}'"
      _fail=$((_fail + 1))
    fi
  else
    fail "${SCENARIO}: ticket not found after 15s for LINE userId=${LINE_USER_ID}"
    _fail=$((_fail + 1))
  fi
else
  note "${SCENARIO}: openssl not available — skipping correct-HMAC webhook test"
fi

info "${SCENARIO}: done. channel_id=${LINE_CHANNEL_ID}"
[ "$_fail" -eq 0 ]
