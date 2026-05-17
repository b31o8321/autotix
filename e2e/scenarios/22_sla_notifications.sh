#!/usr/bin/env bash
# 22_sla_notifications.sh — Notification route CRUD lifecycle
# NOTE: This is a functional-but-no-fire test.
#   It validates the REST CRUD round-trip for notification routes but does NOT
#   verify actual Slack/email delivery (that would require live external endpoints).
#   To test live delivery, set AUTOTIX_NOTIFY_SMTP_HOST / SLACK webhook env vars
#   and call the /test/{id} endpoint manually.
set -euo pipefail

SCENARIO="22_sla_notifications"
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

ROUTE_NAME="e2e-notify-$(random_token | cut -c1-8)"

# ── 1. Create a SLACK_WEBHOOK route ─────────────────────────────────────────
info "${SCENARIO}: create SLACK_WEBHOOK notification route '${ROUTE_NAME}'"
http POST "/api/admin/notifications/routes" \
  "{\"name\":\"${ROUTE_NAME}\",\"eventKind\":\"SLA_BREACHED\",\"channel\":\"SLACK_WEBHOOK\",\"configJson\":\"{\\\"webhookUrl\\\":\\\"https://hooks.slack.com/test\\\",\\\"messageTemplate\\\":\\\"SLA on {ticketId}\\\"}\",\"enabled\":true}"
_check "create slack route → 200" expect_status 200

ROUTE_ID=$(jq_extract '.id')
info "${SCENARIO}: created route id=${ROUTE_ID}"
_check "route id assigned" [ -n "${ROUTE_ID}" ]

# ── 2. List routes → assert present ─────────────────────────────────────────
info "${SCENARIO}: list notification routes"
http GET "/api/admin/notifications/routes"
_check "list routes → 200" expect_status 200
route_names=$(jq_extract '[.[].name]')
_check "route visible in list" assert_contains "route list" "$route_names" "$ROUTE_NAME"

# ── 3. Update route (disable it) ────────────────────────────────────────────
info "${SCENARIO}: update route ${ROUTE_ID} — disable"
http PUT "/api/admin/notifications/routes/${ROUTE_ID}" \
  "{\"name\":\"${ROUTE_NAME}\",\"eventKind\":\"SLA_BREACHED\",\"channel\":\"SLACK_WEBHOOK\",\"configJson\":\"{\\\"webhookUrl\\\":\\\"https://hooks.slack.com/test\\\",\\\"messageTemplate\\\":\\\"SLA on {ticketId}\\\"}\",\"enabled\":false}"
_check "update route → 200" expect_status 200
updated_enabled=$(jq_extract '.enabled')
_check "route disabled" [ "${updated_enabled}" = "false" ]

# ── 4. Create an EMAIL route ─────────────────────────────────────────────────
EMAIL_ROUTE_NAME="e2e-email-notify-$(random_token | cut -c1-8)"
info "${SCENARIO}: create EMAIL notification route '${EMAIL_ROUTE_NAME}'"
http POST "/api/admin/notifications/routes" \
  "{\"name\":\"${EMAIL_ROUTE_NAME}\",\"eventKind\":\"SLA_BREACHED\",\"channel\":\"EMAIL\",\"configJson\":\"{\\\"to\\\":[\\\"ops@example.com\\\"],\\\"subjectTemplate\\\":\\\"SLA breach {ticketId}\\\"}\",\"enabled\":true}"
_check "create email route → 200" expect_status 200
EMAIL_ROUTE_ID=$(jq_extract '.id')

# ── 5. Test endpoint (no live SMTP — dispatcher logs warning and returns ok) ─
info "${SCENARIO}: fire test notification for email route ${EMAIL_ROUTE_ID}"
http GET "/api/admin/notifications/routes/test/${EMAIL_ROUTE_ID}"
_check "test endpoint → 200" expect_status 200

# ── 6. Delete both routes ────────────────────────────────────────────────────
info "${SCENARIO}: delete slack route ${ROUTE_ID}"
http DELETE "/api/admin/notifications/routes/${ROUTE_ID}"
_check "delete slack route → 204" expect_status 204

info "${SCENARIO}: delete email route ${EMAIL_ROUTE_ID}"
http DELETE "/api/admin/notifications/routes/${EMAIL_ROUTE_ID}"
_check "delete email route → 204" expect_status 204

# ── 7. Confirm deleted ───────────────────────────────────────────────────────
info "${SCENARIO}: verify routes no longer exist"
http DELETE "/api/admin/notifications/routes/${ROUTE_ID}"
_check "re-delete returns 404" expect_status 404

# ── Done ─────────────────────────────────────────────────────────────────────
if [ "${_fail}" -gt 0 ]; then
  fail "${SCENARIO}: ${_fail} assertion(s) failed"
  exit 1
fi
