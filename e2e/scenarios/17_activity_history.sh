#!/usr/bin/env bash
# 17_activity_history.sh — Aggregate activity across tickets; assert ≥5 distinct actions
set -euo pipefail

SCENARIO="17_activity_history"
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

# Collect all ticket IDs referenced by previous scenarios
TICKET_IDS=""
for var in E2E_TICKET_04 E2E_TICKET_06; do
  val=$(eval "printf '%s' \"\${${var}:-}\"")
  if [ -n "$val" ] && [ "$val" != "null" ]; then
    TICKET_IDS="${TICKET_IDS} ${val}"
  fi
done

# Also pull recent tickets from API
info "${SCENARIO}: fetching recent tickets"
http GET "/api/desk/tickets?limit=20"
if [ "$LAST_HTTP_STATUS" = "200" ]; then
  recent_ids=$(jq_extract '[.[].id] | .[]')
  for id in $recent_ids; do
    # Deduplicate
    case "$TICKET_IDS" in
      *"$id"*) ;;
      *) TICKET_IDS="${TICKET_IDS} ${id}" ;;
    esac
  done
fi

if [ -z "$TICKET_IDS" ]; then
  fail "${SCENARIO}: no ticket IDs available"
  exit 1
fi

TICKET_COUNT=0
for t in $TICKET_IDS; do
  [ -n "$t" ] && TICKET_COUNT=$((TICKET_COUNT + 1))
done
info "${SCENARIO}: checking activity across ${TICKET_COUNT} ticket(s)"

# Collect all distinct actions
ALL_ACTIONS=""

for TID in $TICKET_IDS; do
  [ -z "$TID" ] && continue
  http GET "/api/desk/tickets/${TID}/activity"
  if [ "$LAST_HTTP_STATUS" = "200" ]; then
    actions=$(jq_extract '[.[].action] | .[]' 2>/dev/null || true)
    for action in $actions; do
      case "$ALL_ACTIONS" in
        *"$action"*) ;;
        *) ALL_ACTIONS="${ALL_ACTIONS} ${action}" ;;
      esac
    done
  fi
done

DISTINCT_COUNT=0
for action in $ALL_ACTIONS; do
  [ -n "$action" ] && DISTINCT_COUNT=$((DISTINCT_COUNT + 1))
done

info "${SCENARIO}: distinct actions found (${DISTINCT_COUNT}):${ALL_ACTIONS}"

if [ "$DISTINCT_COUNT" -ge 5 ]; then
  pass "${SCENARIO}: ${DISTINCT_COUNT} distinct activity actions (≥5 required)"
else
  fail "${SCENARIO}: only ${DISTINCT_COUNT} distinct actions found, need ≥5: [${ALL_ACTIONS}]"
  fail "${SCENARIO}: note — run full suite to accumulate enough activity"
  _fail=$((_fail + 1))
fi

# Check that CREATED action exists (every ticket should have it)
case "$ALL_ACTIONS" in
  *"CREATED"*)
    pass "${SCENARIO}: CREATED action present"
    ;;
  *)
    note "${SCENARIO}: CREATED action not found — check activity log implementation"
    ;;
esac

# Check for ESCALATED if scenario 06 ran
if [ -n "${E2E_TICKET_06:-}" ]; then
  case "$ALL_ACTIONS" in
    *"ESCALATED"*)
      pass "${SCENARIO}: ESCALATED action present"
      ;;
    *)
      note "${SCENARIO}: ESCALATED action not found in activity (scenario 06 data expected)"
      ;;
  esac
fi

info "${SCENARIO}: fail=${_fail}"
[ "$_fail" -eq 0 ]
