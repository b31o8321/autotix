#!/usr/bin/env bash
# 05_auto_reply_ai.sh — AI auto-reply test
# Skip if SKIP_AI=1 or if AI config endpoint looks like a test endpoint.
set -euo pipefail

SCENARIO="05_auto_reply_ai"

# ── Skip checks ───────────────────────────────────────────────────────────────
if [ "${SKIP_AI:-}" = "1" ]; then
  note "${SCENARIO}: SKIP_AI=1 — skipping"
  exit 77  # 77 = skip convention
fi

# Check AI config — skip if endpoint is a placeholder
http GET /api/admin/ai
ai_endpoint=$(jq_extract '.endpoint')
case "$ai_endpoint" in
  *"test"* | *"mock"* | *"test-e2e"* | "" | "null")
    note "${SCENARIO}: AI endpoint '${ai_endpoint}' looks like a placeholder — skipping"
    exit 77
    ;;
esac

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
  fail "${SCENARIO}: no webhook token (run scenario 03 first)"
  exit 1
fi

EXT_ID="e2e-05-$(random_token)"
CUSTOMER_EMAIL="e2e-ai-$(random_token)@e2e.test"

# ── 1. POST inbound ───────────────────────────────────────────────────────────
info "${SCENARIO}: POST inbound"
http_unauth POST "/v2/webhook/CUSTOM/${E2E_TEST_WEBHOOK_TOKEN}" \
  "{\"externalTicketId\":\"${EXT_ID}\",\"customerIdentifier\":\"${CUSTOMER_EMAIL}\",\"message\":\"Please help me with my order\",\"eventType\":\"NEW_TICKET\"}"
_check "webhook → 2xx" expect_2xx

# Find ticket
TICKET_ID=""
poll_until 15 find_ticket_by_ext_id "${EXT_ID}" || { fail "${SCENARIO}: ticket not created"; exit 1; }
pass "${SCENARIO}: ticket found id=${TICKET_ID}"

# ── 2. Poll for AI outbound message ──────────────────────────────────────────
info "${SCENARIO}: polling for AI outbound message (up to 30s)"

_poll_ai_reply() {
  http GET "/api/desk/tickets/${TICKET_ID}/messages"
  ai_count=$(jq_extract '[.[] | select(.direction == "OUTBOUND" and (.author == "ai" or (.author | test("ai";"i"))))] | length' 2>/dev/null || echo "0")
  [ "${ai_count:-0}" -gt 0 ]
}

if poll_until 30 _poll_ai_reply; then
  pass "${SCENARIO}: AI outbound message received"
else
  fail "${SCENARIO}: no AI outbound message within 30s"
  _fail=$((_fail + 1))
fi

# ── 3. Assert status moved to WAITING_ON_CUSTOMER ────────────────────────────
http GET "/api/desk/tickets/${TICKET_ID}"
_check "status=WAITING_ON_CUSTOMER after AI reply" expect_json '.status' "WAITING_ON_CUSTOMER"

info "${SCENARIO}: fail=${_fail}"
[ "$_fail" -eq 0 ]
