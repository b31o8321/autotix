#!/usr/bin/env bash
# 03_channel_setup.sh — CUSTOM channel creation + configuration
# Creates channel, saves id+token to /tmp/e2e-channel.env for downstream scenarios.
# Also exports E2E_TEST_CHANNEL_ID and E2E_TEST_WEBHOOK_TOKEN into env.
set -euo pipefail

SCENARIO="03_channel_setup"
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

CHANNEL_NAME="E2E-Test-$(random_token)"

# ── 1. Create CUSTOM channel ──────────────────────────────────────────────────
info "${SCENARIO}: create CUSTOM channel '${CHANNEL_NAME}'"
http POST /api/admin/channels/connect-api-key \
  "{\"platform\":\"CUSTOM\",\"channelType\":\"CHAT\",\"displayName\":\"${CHANNEL_NAME}\"}"
_check "create channel → 200" expect_status 200

CHANNEL_ID=$(jq_extract '.channelId')
if [ -z "$CHANNEL_ID" ] || [ "$CHANNEL_ID" = "null" ]; then
  fail "${SCENARIO}: no channelId in response — aborting"
  exit 1
fi
pass "${SCENARIO}: channel created id=${CHANNEL_ID}"

# ── 2. List channels, verify ours appears ─────────────────────────────────────
info "${SCENARIO}: list channels"
http GET /api/admin/channels
_check "list → 200" expect_status 200

found=$(jq_extract "[.[] | select(.id == \"${CHANNEL_ID}\")] | length")
if [ "$found" = "1" ]; then
  pass "${SCENARIO}: channel appears in list"
else
  fail "${SCENARIO}: channel not found in list (found=${found})"
  _fail=$((_fail + 1))
fi

# ── 3. Get webhook token from the channel list ────────────────────────────────
WEBHOOK_TOKEN=$(printf '%s' "$LAST_HTTP_BODY" | jq -r "[.[] | select(.id == \"${CHANNEL_ID}\")] | .[0].webhookToken")
if [ -z "$WEBHOOK_TOKEN" ] || [ "$WEBHOOK_TOKEN" = "null" ]; then
  fail "${SCENARIO}: no webhookToken for channel — aborting"
  exit 1
fi
pass "${SCENARIO}: webhookToken acquired"

# ── 4. Toggle autoReply on ────────────────────────────────────────────────────
info "${SCENARIO}: enable autoReply"
http PUT "/api/admin/channels/${CHANNEL_ID}/auto-reply?enabled=true"
_check "autoReply toggle → 200 or 204" expect_ok

# Verify
http GET /api/admin/channels
auto_reply=$(printf '%s' "$LAST_HTTP_BODY" | jq -r "[.[] | select(.id == \"${CHANNEL_ID}\")] | .[0].autoReplyEnabled")
_check "autoReplyEnabled=true in list" assert_eq "autoReply" "$auto_reply" "true"

# ── 5. Persist to /tmp for downstream scenarios ───────────────────────────────
cat > /tmp/e2e-channel.env <<EOF
E2E_TEST_CHANNEL_ID=${CHANNEL_ID}
E2E_TEST_WEBHOOK_TOKEN=${WEBHOOK_TOKEN}
EOF
pass "${SCENARIO}: channel env saved to /tmp/e2e-channel.env"

# Also export into current process (for scenarios sourced in same shell session)
export E2E_TEST_CHANNEL_ID="${CHANNEL_ID}"
export E2E_TEST_WEBHOOK_TOKEN="${WEBHOOK_TOKEN}"

info "${SCENARIO}: channel_id=${CHANNEL_ID} token=${WEBHOOK_TOKEN:0:8}..."

# ── Result ────────────────────────────────────────────────────────────────────
[ "$_fail" -eq 0 ]
