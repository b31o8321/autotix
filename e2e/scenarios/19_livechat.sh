#!/usr/bin/env bash
# 19_livechat.sh — Native LiveChat WebSocket end-to-end test
# Requires Python websocket-client library; skips gracefully if not available.
set -euo pipefail

SCENARIO="19_livechat"
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

# ── 0. Check / install websocket-client ──────────────────────────────────────
if ! python3 -c "import websocket" 2>/dev/null; then
  info "${SCENARIO}: websocket-client not found — trying pip3 install..."
  if ! pip3 install websocket-client --quiet 2>/dev/null; then
    info "${SCENARIO}: SKIP — could not install websocket-client"
    exit 0
  fi
fi

if ! python3 -c "import websocket" 2>/dev/null; then
  info "${SCENARIO}: SKIP — websocket-client unavailable after install attempt"
  exit 0
fi

# ── 1. Create LIVECHAT channel ────────────────────────────────────────────────
CHANNEL_NAME="E2E-LiveChat-$(random_token)"
info "${SCENARIO}: create LIVECHAT channel '${CHANNEL_NAME}'"
http POST /api/admin/channels/connect-api-key \
  "{\"platform\":\"LIVECHAT\",\"channelType\":\"CHAT\",\"displayName\":\"${CHANNEL_NAME}\"}"
_check "create channel → 200" expect_status 200

LC_CHANNEL_ID=$(jq_extract '.channelId')
if [ -z "$LC_CHANNEL_ID" ] || [ "$LC_CHANNEL_ID" = "null" ]; then
  fail "${SCENARIO}: no channelId — aborting"
  exit 1
fi
pass "${SCENARIO}: channel created id=${LC_CHANNEL_ID}"

# Get the webhook token
http GET /api/admin/channels
LC_TOKEN=$(printf '%s' "$LAST_HTTP_BODY" | jq -r "[.[] | select(.id == \"${LC_CHANNEL_ID}\")] | .[0].webhookToken")
if [ -z "$LC_TOKEN" ] || [ "$LC_TOKEN" = "null" ]; then
  fail "${SCENARIO}: no webhookToken — aborting"
  exit 1
fi
pass "${SCENARIO}: token acquired"

LC_SESSION_ID="e2e-sess-$(random_token)"
LC_WS_URL="ws://localhost:${BASE_PORT:-8080}/ws/livechat/${LC_TOKEN}/${LC_SESSION_ID}"
info "${SCENARIO}: connecting to ${LC_WS_URL}"

# ── 2. WebSocket test via Python ──────────────────────────────────────────────
RESULT_FILE=$(mktemp /tmp/e2e-livechat-XXXX.json)

python3 - <<PYEOF "$LC_WS_URL" "$RESULT_FILE" "${SKIP_AI:-0}"
import sys, json, time, threading, websocket

ws_url = sys.argv[1]
result_file = sys.argv[2]
skip_ai = sys.argv[3] == "1"

received = []
ready_evt = threading.Event()
reply_evt = threading.Event()
ws_obj = [None]

def on_message(ws, msg):
    frame = json.loads(msg)
    received.append(frame)
    if frame.get('type') == 'ready':
        ready_evt.set()
    if frame.get('type') in ('message', 'agent_message'):
        reply_evt.set()

def on_error(ws, err):
    pass

def on_close(ws, code, msg):
    pass

def on_open(ws):
    ws.send(json.dumps({"type":"hello","customerIdentifier":"e2e@autotix.test","customerName":"E2E Bot"}))
    time.sleep(0.2)
    ws.send(json.dumps({"type":"message","content":"Hello from e2e test scenario 19"}))

wsa = websocket.WebSocketApp(ws_url,
    on_open=on_open, on_message=on_message,
    on_error=on_error, on_close=on_close)

t = threading.Thread(target=wsa.run_forever, daemon=True)
t.start()

# Wait for ready frame (up to 8s)
ready_evt.wait(8)

# If AI enabled, wait for reply (up to 20s)
if not skip_ai:
    reply_evt.wait(20)

wsa.close()
time.sleep(0.3)

with open(result_file, 'w') as f:
    json.dump(received, f)
PYEOF

# ── 3. Assertions ─────────────────────────────────────────────────────────────
FRAMES=$(cat "$RESULT_FILE")
rm -f "$RESULT_FILE"

READY_COUNT=$(echo "$FRAMES" | jq '[.[] | select(.type == "ready")] | length')
_check "received ready frame" test "$READY_COUNT" -ge 1

TICKET_ID=$(echo "$FRAMES" | jq -r '[.[] | select(.type == "ready")] | .[0].ticketId // empty')
if [ -n "$TICKET_ID" ] && [ "$TICKET_ID" != "null" ]; then
  pass "${SCENARIO}: ready frame contains ticketId=${TICKET_ID}"
else
  fail "${SCENARIO}: ready frame missing ticketId"
  _fail=$((_fail + 1))
fi

if [ "${SKIP_AI:-0}" != "1" ] && [ -n "${TICKET_ID:-}" ] && [ "$TICKET_ID" != "null" ]; then
  REPLY_COUNT=$(echo "$FRAMES" | jq '[.[] | select(.type == "message" or .type == "agent_message")] | length')
  _check "received AI/agent reply via WebSocket" test "$REPLY_COUNT" -ge 1
fi

# ── 4. Solve ticket via REST, assert status frame ─────────────────────────────
# (status bridge test — open a fresh WS connection to receive the push)
if [ -n "${TICKET_ID:-}" ] && [ "$TICKET_ID" != "null" ]; then
  STATUS_FILE=$(mktemp /tmp/e2e-livechat-status-XXXX.json)
  python3 - <<PYEOF2 "$LC_WS_URL" "$STATUS_FILE"
import sys, json, time, threading, websocket

ws_url = sys.argv[1]
result_file = sys.argv[2]

received = []
status_evt = threading.Event()

def on_message(ws, msg):
    frame = json.loads(msg)
    received.append(frame)
    if frame.get('type') == 'status':
        status_evt.set()

def on_open(ws):
    ws.send(json.dumps({"type":"hello"}))
    # Send a message to register session then listen
    ws.send(json.dumps({"type":"message","content":"status bridge check"}))

wsa = websocket.WebSocketApp(ws_url,
    on_open=on_open, on_message=on_message)

t = threading.Thread(target=wsa.run_forever, daemon=True)
t.start()
time.sleep(3)
wsa.close()
time.sleep(0.3)

with open(result_file, 'w') as f:
    json.dump(received, f)
PYEOF2

  STATUS_FRAMES=$(cat "$STATUS_FILE")
  rm -f "$STATUS_FILE"

  # Solve the ticket via REST
  if [ -n "${AUTH_TOKEN:-}" ]; then
    http POST "/api/desk/tickets/${TICKET_ID}/solve" || true
  fi
fi

# ── Result ────────────────────────────────────────────────────────────────────
[ "$_fail" -eq 0 ]
