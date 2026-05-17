#!/usr/bin/env bash
# 23_shopify_channel.sh — Shopify channel end-to-end test
#
# Tests:
#   1. Create SHOPIFY channel via REST
#   2. GET /channels — verify SHOPIFY channel appears
#   3. POST orders/create webhook → ticket created with correct subject
#   4. Set webhook secret; post bad HMAC → 403
#   5. Post correct HMAC → ticket created (acceptance)
set -euo pipefail

SCENARIO="23_shopify_channel"
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

# ── 1. Create SHOPIFY channel ─────────────────────────────────────────────────
CHANNEL_NAME="E2E-Shopify-$(random_token)"
info "${SCENARIO}: create SHOPIFY channel '${CHANNEL_NAME}'"
http POST /api/admin/channels/connect-api-key \
  "{\"platform\":\"SHOPIFY\",\"channelType\":\"EMAIL\",\"displayName\":\"${CHANNEL_NAME}\",\"credentials\":{\"shop_domain\":\"e2e-store.myshopify.com\",\"admin_api_token\":\"shpat_e2etest000\"}}"
_check "create SHOPIFY channel → 200" expect_status 200

SHOPIFY_CHANNEL_ID=$(jq_extract '.channelId')
if [ -z "$SHOPIFY_CHANNEL_ID" ] || [ "$SHOPIFY_CHANNEL_ID" = "null" ]; then
  fail "${SCENARIO}: no channelId returned — aborting"
  exit 1
fi
pass "${SCENARIO}: SHOPIFY channel created id=${SHOPIFY_CHANNEL_ID}"

# ── 2. Verify channel appears in list ─────────────────────────────────────────
info "${SCENARIO}: GET /api/admin/channels"
http GET /api/admin/channels
_check "list channels → 200" expect_status 200

found=$(jq_extract "[.[] | select(.id == \"${SHOPIFY_CHANNEL_ID}\")] | length")
_check "channel appears in list" assert_eq "found" "$found" "1"

platform=$(jq_extract "[.[] | select(.id == \"${SHOPIFY_CHANNEL_ID}\")] | .[0].platform")
_check "platform=SHOPIFY" assert_eq "platform" "$platform" "SHOPIFY"

SHOPIFY_WEBHOOK_TOKEN=$(jq_extract "[.[] | select(.id == \"${SHOPIFY_CHANNEL_ID}\")] | .[0].webhookToken")
if [ -z "$SHOPIFY_WEBHOOK_TOKEN" ] || [ "$SHOPIFY_WEBHOOK_TOKEN" = "null" ]; then
  fail "${SCENARIO}: no webhookToken — aborting"
  exit 1
fi
pass "${SCENARIO}: webhookToken acquired: ${SHOPIFY_WEBHOOK_TOKEN:0:8}..."

# ── 3. POST orders/create webhook (no secret → accepted) ──────────────────────
EXT_ORDER_ID="e2e-23-order-$(random_token)"
CUSTOMER_EMAIL="e2e-shopify-$(random_token)@example.test"
ORDER_NUM="E2E-001"

SHOPIFY_ORDER_BODY="{
  \"id\": \"${EXT_ORDER_ID}\",
  \"order_number\": \"${ORDER_NUM}\",
  \"total_price\": \"99.00\",
  \"email\": \"${CUSTOMER_EMAIL}\",
  \"customer\": {
    \"first_name\": \"TestFirst\",
    \"last_name\": \"TestLast\",
    \"email\": \"${CUSTOMER_EMAIL}\"
  },
  \"line_items\": [{\"id\": 1, \"title\": \"Widget\"}]
}"

info "${SCENARIO}: POST orders/create webhook (no signature)"
http_unauth POST "/v2/webhook/SHOPIFY/${SHOPIFY_WEBHOOK_TOKEN}" \
  "$SHOPIFY_ORDER_BODY" \
  2>/dev/null || true

# Override to also send topic header — we need curl with extra headers.
# Re-issue with topic header using curl directly.
RAW_RESP=$(curl -s -w "\n__STATUS__:%{http_code}" -X POST \
  "${BASE_URL}/v2/webhook/SHOPIFY/${SHOPIFY_WEBHOOK_TOKEN}" \
  -H "Content-Type: application/json" \
  -H "X-Shopify-Topic: orders/create" \
  -H "X-Shopify-Shop-Domain: e2e-store.myshopify.com" \
  --data-raw "$SHOPIFY_ORDER_BODY" 2>/dev/null)
LAST_HTTP_STATUS=$(printf '%s' "$RAW_RESP" | grep -o '__STATUS__:[0-9]*' | cut -d: -f2)
LAST_HTTP_BODY=$(printf '%s' "$RAW_RESP" | sed 's/__STATUS__:[0-9]*$//')

_check "orders/create webhook → 2xx" expect_2xx

# ── 4. Poll for ticket ─────────────────────────────────────────────────────────
info "${SCENARIO}: polling for ticket from orders/create (up to 15s)"
TICKET_ID=""
if poll_until 15 find_ticket_by_ext_id "${EXT_ORDER_ID}"; then
  pass "${SCENARIO}: ticket found id=${TICKET_ID}"
else
  fail "${SCENARIO}: ticket not found after 15s"
  _fail=$((_fail + 1))
  # Keep going for remaining checks
fi

if [ -n "${TICKET_ID:-}" ] && [ "${TICKET_ID}" != "null" ]; then
  http GET "/api/desk/tickets/${TICKET_ID}"
  _check "ticket detail → 200" expect_status 200
  subject=$(jq_extract '.subject')
  _check "subject contains order number" assert_contains "subject" "$subject" "${ORDER_NUM}"
fi

# ── 5. Set webhook secret ─────────────────────────────────────────────────────
SHOPIFY_SECRET="e2e-shared-secret-$(random_token)"
info "${SCENARIO}: set webhook_shared_secret"
http PUT "/api/admin/channels/${SHOPIFY_CHANNEL_ID}/secret" \
  "{\"secret\":\"${SHOPIFY_SECRET}\"}"
_check "set secret → 200 or 204" expect_ok

# ── 6. Post webhook with bad HMAC → expect 4xx ────────────────────────────────
EXT_ORDER_ID2="e2e-23-bad-$(random_token)"
ORDER_BODY2="{\"id\":\"${EXT_ORDER_ID2}\",\"order_number\":\"E2E-002\",\"total_price\":\"10.00\",\"email\":\"bad@test.com\",\"customer\":{\"first_name\":\"Bad\",\"last_name\":\"Sig\",\"email\":\"bad@test.com\"},\"line_items\":[]}"

info "${SCENARIO}: POST orders/create with bad HMAC — expect rejection"
RAW_RESP2=$(curl -s -w "\n__STATUS__:%{http_code}" -X POST \
  "${BASE_URL}/v2/webhook/SHOPIFY/${SHOPIFY_WEBHOOK_TOKEN}" \
  -H "Content-Type: application/json" \
  -H "X-Shopify-Topic: orders/create" \
  -H "X-Shopify-Shop-Domain: e2e-store.myshopify.com" \
  -H "X-Shopify-Hmac-Sha256: aW52YWxpZHNpZ25hdHVyZQ==" \
  --data-raw "$ORDER_BODY2" 2>/dev/null)
LAST_HTTP_STATUS=$(printf '%s' "$RAW_RESP2" | grep -o '__STATUS__:[0-9]*' | cut -d: -f2)
LAST_HTTP_BODY=$(printf '%s' "$RAW_RESP2" | sed 's/__STATUS__:[0-9]*$//')

if [ "${LAST_HTTP_STATUS}" = "401" ] || [ "${LAST_HTTP_STATUS}" = "403" ] || [ "${LAST_HTTP_STATUS}" = "400" ]; then
  pass "${SCENARIO}: bad HMAC correctly rejected (HTTP ${LAST_HTTP_STATUS})"
else
  fail "${SCENARIO}: bad HMAC should be rejected; got HTTP ${LAST_HTTP_STATUS}"
  _fail=$((_fail + 1))
fi

# ── 7. Post webhook with correct HMAC → accepted ──────────────────────────────
EXT_ORDER_ID3="e2e-23-good-$(random_token)"
ORDER_BODY3="{\"id\":\"${EXT_ORDER_ID3}\",\"order_number\":\"E2E-003\",\"total_price\":\"55.00\",\"email\":\"good@test.com\",\"customer\":{\"first_name\":\"Good\",\"last_name\":\"Sig\",\"email\":\"good@test.com\"},\"line_items\":[{\"id\":1,\"title\":\"Widget\"}]}"

# Compute HMAC-SHA256 in shell using openssl
if command -v openssl >/dev/null 2>&1; then
  CORRECT_SIG=$(printf '%s' "${ORDER_BODY3}" | openssl dgst -sha256 -hmac "${SHOPIFY_SECRET}" -binary | base64)

  info "${SCENARIO}: POST orders/create with correct HMAC"
  RAW_RESP3=$(curl -s -w "\n__STATUS__:%{http_code}" -X POST \
    "${BASE_URL}/v2/webhook/SHOPIFY/${SHOPIFY_WEBHOOK_TOKEN}" \
    -H "Content-Type: application/json" \
    -H "X-Shopify-Topic: orders/create" \
    -H "X-Shopify-Shop-Domain: e2e-store.myshopify.com" \
    -H "X-Shopify-Hmac-Sha256: ${CORRECT_SIG}" \
    --data-raw "$ORDER_BODY3" 2>/dev/null)
  LAST_HTTP_STATUS=$(printf '%s' "$RAW_RESP3" | grep -o '__STATUS__:[0-9]*' | cut -d: -f2)
  LAST_HTTP_BODY=$(printf '%s' "$RAW_RESP3" | sed 's/__STATUS__:[0-9]*$//')

  _check "correct HMAC webhook → 2xx" expect_2xx

  # Poll for ticket
  info "${SCENARIO}: polling for ticket from correct-HMAC webhook (up to 15s)"
  TICKET_ID=""
  if poll_until 15 find_ticket_by_ext_id "${EXT_ORDER_ID3}"; then
    pass "${SCENARIO}: ticket from correct-HMAC webhook found id=${TICKET_ID}"
  else
    fail "${SCENARIO}: ticket from correct-HMAC webhook not found after 15s"
    _fail=$((_fail + 1))
  fi
else
  note "${SCENARIO}: openssl not available — skipping correct-HMAC test"
fi

info "${SCENARIO}: done. channel_id=${SHOPIFY_CHANNEL_ID}"
[ "$_fail" -eq 0 ]
