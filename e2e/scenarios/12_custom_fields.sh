#!/usr/bin/env bash
# 12_custom_fields.sh — Custom field CRUD on tickets
set -euo pipefail

SCENARIO="12_custom_fields"
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

# Use ticket from 04 or find any
TICKET_ID="${E2E_TICKET_04:-}"
if [ -z "$TICKET_ID" ] || [ "$TICKET_ID" = "null" ]; then
  http GET "/api/desk/tickets?limit=10"
  TICKET_ID=$(jq_extract '.[0].id')
fi
if [ -z "$TICKET_ID" ] || [ "$TICKET_ID" = "null" ]; then
  fail "${SCENARIO}: no ticket available"
  exit 1
fi
info "${SCENARIO}: using ticket ${TICKET_ID}"

FIELD_KEY="e2e_order_id_$(random_token | cut -c1-6)"

# ── 1. Create custom field definition ────────────────────────────────────────
info "${SCENARIO}: create custom field definition key=${FIELD_KEY}"
http POST "/api/admin/custom-fields" \
  "{\"name\":\"E2E Order ID\",\"key\":\"${FIELD_KEY}\",\"type\":\"TEXT\",\"appliesTo\":\"TICKET\",\"required\":false,\"displayOrder\":99}"

if [ "$LAST_HTTP_STATUS" = "200" ] || [ "$LAST_HTTP_STATUS" = "201" ]; then
  pass "${SCENARIO}: custom field definition created"
elif [ "$LAST_HTTP_STATUS" = "409" ]; then
  note "${SCENARIO}: custom field key already exists — proceeding"
else
  fail "${SCENARIO}: unexpected status ${LAST_HTTP_STATUS} creating custom field"
  _fail=$((_fail + 1))
fi

# ── 2. Set custom field value on ticket ───────────────────────────────────────
info "${SCENARIO}: set custom field ${FIELD_KEY}=e2e-1234"
http PUT "/api/desk/tickets/${TICKET_ID}/custom-fields/${FIELD_KEY}" \
  '{"value":"e2e-1234"}'
_check "set custom field → 200 or 204" expect_ok

# ── 3. GET ticket — verify custom field ──────────────────────────────────────
http GET "/api/desk/tickets/${TICKET_ID}"
field_val=$(jq_extract ".customFields.\"${FIELD_KEY}\"")
if [ "$field_val" = "e2e-1234" ]; then
  pass "${SCENARIO}: customField ${FIELD_KEY}=e2e-1234"
else
  fail "${SCENARIO}: expected e2e-1234, got '${field_val}'"
  _fail=$((_fail + 1))
fi

# ── 4. Clear custom field (set to null) ──────────────────────────────────────
info "${SCENARIO}: clear custom field ${FIELD_KEY}"
http PUT "/api/desk/tickets/${TICKET_ID}/custom-fields/${FIELD_KEY}" \
  '{"value":null}'
_check "clear custom field → 200 or 204" expect_ok

http GET "/api/desk/tickets/${TICKET_ID}"
field_after=$(jq_extract ".customFields.\"${FIELD_KEY}\"")
if [ -z "$field_after" ] || [ "$field_after" = "null" ]; then
  pass "${SCENARIO}: custom field cleared"
else
  fail "${SCENARIO}: custom field not cleared, value=${field_after}"
  _fail=$((_fail + 1))
fi

info "${SCENARIO}: fail=${_fail}"
[ "$_fail" -eq 0 ]
