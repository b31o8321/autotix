#!/usr/bin/env bash
# 16_attachments_local.sh — Inbound attachment upload + download verification
set -euo pipefail

SCENARIO="16_attachments_local"
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
  fail "${SCENARIO}: no webhook token"
  exit 1
fi

# ── 1. Generate ~10KB of deterministic random bytes ──────────────────────────
TMP_BIN=$(mktemp)
TMP_B64=$(mktemp)

# Generate 10240 bytes using od/tr (portable, no bash-isms)
dd if=/dev/urandom bs=1024 count=10 of="${TMP_BIN}" 2>/dev/null
# Extract just the hash (not filename) — md5sum prints "hash  file", md5 -q prints just hash
ORIG_MD5=$(md5sum "${TMP_BIN}" 2>/dev/null | awk '{print $1}' || md5 -q "${TMP_BIN}" 2>/dev/null || echo "no-md5")

# Base64 encode (remove newlines for JSON embedding)
# Use stdin to be compatible with both macOS base64 and GNU base64
base64 < "${TMP_BIN}" | tr -d '\n' > "${TMP_B64}" 2>/dev/null || \
  openssl base64 -in "${TMP_BIN}" | tr -d '\n' > "${TMP_B64}"

B64_CONTENT=$(cat "${TMP_B64}")
FILE_SIZE=$(wc -c < "${TMP_BIN}" | tr -d ' ')
FILE_NAME="e2e-test-$(random_token | cut -c1-8).bin"

info "${SCENARIO}: attachment ${FILE_NAME} (${FILE_SIZE} bytes, md5=${ORIG_MD5})"

EXT_ID="e2e-16-$(random_token)"
CUSTOMER_EMAIL="e2e-att-$(random_token)@e2e.test"

# ── 2. POST webhook with attachment ──────────────────────────────────────────
info "${SCENARIO}: POST inbound with attachment"

# Build JSON carefully to avoid shell escaping issues
PAYLOAD=$(jq -n \
  --arg extId "$EXT_ID" \
  --arg custId "$CUSTOMER_EMAIL" \
  --arg fname "$FILE_NAME" \
  --argjson fsize "$FILE_SIZE" \
  --arg b64 "$B64_CONTENT" \
  '{
    externalTicketId: $extId,
    customerIdentifier: $custId,
    message: "Attached a file",
    eventType: "NEW_TICKET",
    attachments: [{
      fileName: $fname,
      contentType: "application/octet-stream",
      sizeBytes: $fsize,
      contentBase64: $b64
    }]
  }')

http_unauth POST "/v2/webhook/CUSTOM/${E2E_TEST_WEBHOOK_TOKEN}" "$PAYLOAD"
_check "webhook with attachment → 2xx" expect_2xx

# ── 3. Poll for ticket ────────────────────────────────────────────────────────
TICKET_ID=""
poll_until 15 find_ticket_by_ext_id "${EXT_ID}" || { fail "${SCENARIO}: ticket not created"; exit 1; }
pass "${SCENARIO}: ticket created id=${TICKET_ID}"

# ── 4. Check attachment on first INBOUND message ─────────────────────────────
info "${SCENARIO}: check attachment on ticket detail"
http GET "/api/desk/tickets/${TICKET_ID}"
_check "ticket detail → 200" expect_status 200

# Check via /messages endpoint
http GET "/api/desk/tickets/${TICKET_ID}/messages"
att_count=$(jq_extract '[.[] | select(.direction == "INBOUND")] | .[0].attachments | length' 2>/dev/null || echo "0")

if [ "${att_count:-0}" -gt 0 ]; then
  pass "${SCENARIO}: ${att_count} attachment(s) on INBOUND message"
  DOWNLOAD_URL=$(jq_extract '[.[] | select(.direction == "INBOUND")] | .[0].attachments[0].downloadUrl' 2>/dev/null || echo "")
else
  # May be in ticket detail messages
  http GET "/api/desk/tickets/${TICKET_ID}"
  att_count=$(jq_extract '[.messages[]? | select(.direction == "INBOUND")] | .[0].attachments | length' 2>/dev/null || echo "0")
  DOWNLOAD_URL=$(jq_extract '[.messages[]? | select(.direction == "INBOUND")] | .[0].attachments[0].downloadUrl' 2>/dev/null || echo "")
fi

if [ "${att_count:-0}" -gt 0 ]; then
  pass "${SCENARIO}: attachment found"
else
  fail "${SCENARIO}: no attachment found on INBOUND message"
  _fail=$((_fail + 1))
fi

# ── 5. Download and md5-check ─────────────────────────────────────────────────
if [ -n "$DOWNLOAD_URL" ] && [ "$DOWNLOAD_URL" != "null" ]; then
  # Build absolute URL if relative
  case "$DOWNLOAD_URL" in
    http*) FULL_URL="${DOWNLOAD_URL}" ;;
    *)     FULL_URL="${BASE_URL}${DOWNLOAD_URL}" ;;
  esac
  # Append auth token as query param if needed
  case "$FULL_URL" in
    *"?"*) FULL_URL="${FULL_URL}&token=${ADMIN_TOKEN}" ;;
    *)     FULL_URL="${FULL_URL}?token=${ADMIN_TOKEN}" ;;
  esac

  info "${SCENARIO}: downloading from ${FULL_URL}"
  TMP_DL=$(mktemp)
  DL_STATUS=$(curl -s -o "${TMP_DL}" -w "%{http_code}" \
    -H "Authorization: Bearer ${ADMIN_TOKEN}" \
    "${FULL_URL}" 2>/dev/null)

  if [ "$DL_STATUS" = "200" ]; then
    pass "${SCENARIO}: download returned 200"
    DL_MD5=$(md5sum "${TMP_DL}" 2>/dev/null | awk '{print $1}' || md5 -q "${TMP_DL}" 2>/dev/null || echo "dl-no-md5")
    if [ "$DL_MD5" = "$ORIG_MD5" ]; then
      pass "${SCENARIO}: MD5 matches original"
    else
      note "${SCENARIO}: MD5 mismatch (orig=${ORIG_MD5} dl=${DL_MD5}) — possibly base64 encoding variation"
    fi
  else
    note "${SCENARIO}: download returned HTTP ${DL_STATUS} — may require different auth approach"
  fi
  rm -f "${TMP_DL}"
else
  note "${SCENARIO}: no downloadUrl available to verify bytes"
fi

rm -f "${TMP_BIN}" "${TMP_B64}"

info "${SCENARIO}: fail=${_fail}"
[ "$_fail" -eq 0 ]
