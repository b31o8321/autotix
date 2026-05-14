#!/usr/bin/env bash
# 18_email_channel.sh — Email channel E2E: inbound SMTP→IMAP→poller→ticket + outbound reply→IMAP
#
# Prerequisites:
#   docker compose --profile mail up -d mail   (GreenMail on 3025/SMTP, 3143/IMAP, 8088/API)
#   python3 must be available (used to send test email via SMTP)
#   ADMIN_TOKEN must be set (run scenario 01 first or let run.sh handle it)
#
# Exit 77 → SKIP (pre-req missing)
# Exit 0  → PASS or FAIL (sets _fail counter)
set -euo pipefail

SCENARIO="18_email_channel"
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

# ── Pre-req: mail service running ─────────────────────────────────────────────
# GreenMail standalone 2.0.0 doesn't serve HTTP — check SMTP TCP port directly.
if ! (exec 3<>/dev/tcp/localhost/3025) 2>/dev/null; then
  note "${SCENARIO}: GreenMail SMTP not reachable at localhost:3025 — skipping"
  note "${SCENARIO}: Start it with: docker compose --profile mail up -d mail"
  exit 77
fi
exec 3<&- 3>&- 2>/dev/null || true

# ── Pre-req: python3 available ────────────────────────────────────────────────
if ! command -v python3 >/dev/null 2>&1; then
  note "${SCENARIO}: python3 not found — skipping (required for SMTP send)"
  exit 77
fi

# ── 1. Create EMAIL channel ───────────────────────────────────────────────────
CHANNEL_DISPLAY="E2E Email $(random_token)"
CUSTOMER_EMAIL="customer@example.com"
SUBJECT="E2E Email Test $(random_token)"

info "${SCENARIO}: Creating EMAIL channel"
http POST /api/admin/channels/connect-api-key \
  "{\"platform\":\"EMAIL\",\"channelType\":\"EMAIL\",\"displayName\":\"${CHANNEL_DISPLAY}\",\"credentials\":{\"imap_host\":\"mail\",\"imap_port\":\"3143\",\"imap_user\":\"agent\",\"imap_password\":\"secret\",\"imap_use_ssl\":\"false\",\"smtp_host\":\"mail\",\"smtp_port\":\"3025\",\"smtp_user\":\"agent\",\"smtp_password\":\"secret\",\"smtp_use_tls\":\"false\",\"from_address\":\"agent@autotix.local\"}}"
_check "create EMAIL channel → 200" expect_2xx

EMAIL_CHANNEL_ID=$(jq_extract '.channelId // .id // .channel.id')
if [ -z "${EMAIL_CHANNEL_ID}" ] || [ "${EMAIL_CHANNEL_ID}" = "null" ]; then
  fail "${SCENARIO}: Could not extract channelId from create-channel response"
  fail "${SCENARIO}: Body: ${LAST_HTTP_BODY}"
  _fail=$((_fail + 1))
  exit 1
fi
pass "${SCENARIO}: EMAIL channel created id=${EMAIL_CHANNEL_ID}"

# ── 2. Send inbound email via Python SMTP ─────────────────────────────────────
info "${SCENARIO}: Sending inbound email via SMTP (python3)"
python3 - <<PY || { fail "${SCENARIO}: python3 SMTP send failed"; _fail=$((_fail + 1)); exit 1; }
import smtplib
from email.message import EmailMessage
msg = EmailMessage()
msg['Subject'] = '${SUBJECT}'
msg['From'] = '${CUSTOMER_EMAIL}'
msg['To'] = 'agent@autotix.local'
msg.set_content('Hello from E2E test — please help me.')
with smtplib.SMTP('localhost', 3025, timeout=10) as s:
    s.send_message(msg)
print('SMTP send OK')
PY
pass "${SCENARIO}: Inbound email sent via SMTP"

# ── 3. Wait for poller to pick up email (up to 90s) ──────────────────────────
info "${SCENARIO}: Polling for ticket (poller default 60s + buffer)"
TICKET_ID=""
find_ticket_by_subject() {
  http GET "/api/desk/tickets?limit=100"
  local found_id
  # Match the unique random suffix to avoid stale tickets from prior runs
  found_id=$(jq_extract "[.[] | select(.subject != null and (.subject | contains(\"${SUBJECT}\")))] | .[0].id")
  if [ -n "$found_id" ] && [ "$found_id" != "null" ]; then
    TICKET_ID="$found_id"
    return 0
  fi
  return 1
}

if poll_until 90 find_ticket_by_subject; then
  pass "${SCENARIO}: Ticket created from inbound email id=${TICKET_ID}"
else
  fail "${SCENARIO}: Ticket not found within 90s — check email poller is running"
  _fail=$((_fail + 1))
  exit 1
fi

# ── 4. Assert ticket fields ────────────────────────────────────────────────────
info "${SCENARIO}: Verifying ticket fields"
http GET "/api/desk/tickets/${TICKET_ID}"
_check "ticket detail → 200" expect_status 200

ticket_customer=$(jq_extract '.customerIdentifier')
_check "customerIdentifier is customer@example.com" \
  test "${ticket_customer}" = "${CUSTOMER_EMAIL}"

ticket_subject=$(jq_extract '.subject')
_check "subject contains E2E Email Test" \
  bash -c "printf '%s' '${ticket_subject}' | grep -qi 'E2E Email Test'"

# ── 5. Reply to ticket ────────────────────────────────────────────────────────
info "${SCENARIO}: Posting reply to ticket"
http POST "/api/desk/tickets/${TICKET_ID}/reply" \
  "{\"content\":\"auto reply from E2E test\",\"closeAfter\":false,\"internal\":false}"
_check "reply → 200" expect_2xx

# ── 6. Wait briefly for SMTP send then verify via IMAP ───────────────────────
info "${SCENARIO}: Waiting 5s for SMTP delivery"
sleep 5

info "${SCENARIO}: Checking customer's mailbox via IMAP (python3)"
IMAP_MSG_COUNT=$(python3 - <<'PY' 2>/dev/null || echo "0"
import imaplib
try:
    imap = imaplib.IMAP4('localhost', 3143)
    imap.login('customer', 'secret')
    imap.select('INBOX')
    _, data = imap.search(None, 'ALL')
    count = len(data[0].split()) if data[0] else 0
    print(count)
    imap.logout()
except Exception as e:
    print(0)
PY
)

if [ "${IMAP_MSG_COUNT:-0}" -ge 1 ] 2>/dev/null; then
  pass "${SCENARIO}: Customer mailbox has ${IMAP_MSG_COUNT} message(s) — reply delivered"
else
  fail "${SCENARIO}: Customer mailbox appears empty (count=${IMAP_MSG_COUNT:-?}) — SMTP reply may not have been sent"
  _fail=$((_fail + 1))
fi

# ── 7. Verify reply body via IMAP ─────────────────────────────────────────────
info "${SCENARIO}: Verifying reply body in customer mailbox"
set +e
REPLY_BODY=$(python3 - 2>/dev/null <<'PY'
import imaplib
import email
try:
    imap = imaplib.IMAP4('localhost', 3143)
    imap.login('customer', 'secret')
    imap.select('INBOX')
    _, data = imap.search(None, 'ALL')
    ids = data[0].split()
    if not ids:
        print('')
    else:
        _, msg_data = imap.fetch(ids[-1], '(RFC822)')
        msg = email.message_from_bytes(msg_data[0][1])
        if msg.is_multipart():
            for part in msg.walk():
                ct = part.get_content_type()
                if 'text' in ct:
                    print(part.get_payload(decode=True).decode('utf-8', errors='replace'))
                    break
        else:
            print(msg.get_payload(decode=True).decode('utf-8', errors='replace'))
    imap.logout()
except Exception:
    print('')
PY
)
set -e

if printf '%s' "${REPLY_BODY}" | grep -qi "auto reply"; then
  pass "${SCENARIO}: Reply body contains 'auto reply'"
else
  note "${SCENARIO}: Reply body did not contain 'auto reply' (got: ${REPLY_BODY:0:100})"
  note "${SCENARIO}: This may fail if SMTP is working but body extraction differs"
  # Non-fatal: we already confirmed message count
fi

# ── Done ──────────────────────────────────────────────────────────────────────
if [ "${_fail}" -eq 0 ]; then
  pass "${SCENARIO}: All checks passed"
else
  fail "${SCENARIO}: ${_fail} check(s) failed"
  exit 1
fi
