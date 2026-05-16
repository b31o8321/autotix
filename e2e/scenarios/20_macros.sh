#!/usr/bin/env bash
# 20_macros.sh — Macro CRUD lifecycle
set -euo pipefail

SCENARIO="20_macros"
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

MACRO_NAME="e2e-macro-$(random_token | cut -c1-8)"

# ── 1. Create macro via admin endpoint ───────────────────────────────────────
info "${SCENARIO}: create macro '${MACRO_NAME}'"
http POST "/api/admin/macros" \
  "{\"name\":\"${MACRO_NAME}\",\"bodyMarkdown\":\"Thank you for contacting us.\",\"category\":\"e2e\",\"availableTo\":\"AGENT\"}"
_check "create macro → 200" expect_status 200

MACRO_ID=$(jq_extract '.id')
info "${SCENARIO}: created macro id=${MACRO_ID}"

# ── 2. List as agent → assert visible ────────────────────────────────────────
info "${SCENARIO}: list macros as admin (desk endpoint)"
http GET "/api/desk/macros"
_check "list macros → 200" expect_status 200
macro_names=$(jq_extract '[.[].name]')
_check "macro visible in desk list" assert_contains "desk macros" "$macro_names" "$MACRO_NAME"

# ── 3. Mark as used → assert usageCount increments ───────────────────────────
info "${SCENARIO}: record macro usage"
http POST "/api/desk/macros/${MACRO_ID}/use" "{}"
_check "mark used → 204" expect_status 204

http GET "/api/admin/macros"
_check "list admin macros → 200" expect_status 200
usage=$(jq_extract --arg id "$MACRO_ID" '[.[] | select(.id == ($id | tonumber)) | .usageCount][0]')
info "${SCENARIO}: usageCount=${usage}"
_check "usageCount is 1" [ "$usage" = "1" ]

# ── 4. Update macro ───────────────────────────────────────────────────────────
info "${SCENARIO}: update macro body"
http PUT "/api/admin/macros/${MACRO_ID}" \
  "{\"name\":\"${MACRO_NAME}\",\"bodyMarkdown\":\"Updated body text\",\"category\":\"e2e\",\"availableTo\":\"AGENT\"}"
_check "update macro → 200" expect_status 200
updated_body=$(jq_extract '.bodyMarkdown')
_check "body updated" assert_contains "bodyMarkdown" "$updated_body" "Updated"

# ── 5. Delete macro ───────────────────────────────────────────────────────────
info "${SCENARIO}: delete macro"
http DELETE "/api/admin/macros/${MACRO_ID}"
_check "delete macro → 204" expect_status 204

# ── 6. Verify 404 via list absence ───────────────────────────────────────────
http GET "/api/admin/macros"
_check "list after delete → 200" expect_status 200
names_after=$(jq_extract '[.[].name]')
case "$names_after" in
  *"$MACRO_NAME"*)
    fail "${SCENARIO}: deleted macro still present in list"
    _fail=$((_fail + 1))
    ;;
  *)
    pass "${SCENARIO}: deleted macro absent from list"
    ;;
esac

info "${SCENARIO}: fail=${_fail}"
[ "$_fail" -eq 0 ]
