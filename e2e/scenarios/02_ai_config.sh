#!/usr/bin/env bash
# 02_ai_config.sh — AI config admin tests
# Tests: GET masks apiKey, PUT updates endpoint/model, GET reflects changes,
#        PUT global flag.
set -euo pipefail

SCENARIO="02_ai_config"
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

# ── 1. GET masks apiKey ───────────────────────────────────────────────────────
info "${SCENARIO}: GET /api/admin/ai"
http GET /api/admin/ai
_check "GET → 200" expect_status 200

api_key_val=$(jq_extract '.apiKey')
case "$api_key_val" in
  ""|"null")
    pass "${SCENARIO}: apiKey is empty/null (not configured yet — acceptable)"
    ;;
  "sk-***"*)
    pass "${SCENARIO}: apiKey is masked"
    ;;
  "***")
    pass "${SCENARIO}: apiKey is masked (***)"
    ;;
  *)
    # Check it doesn't look like a real key (>20 chars, not masked)
    if [ "${#api_key_val}" -gt 20 ]; then
      fail "${SCENARIO}: apiKey appears to be unmasked: ${api_key_val:0:8}..."
      _fail=$((_fail + 1))
    else
      note "${SCENARIO}: apiKey value='${api_key_val}' — may be short placeholder"
    fi
    ;;
esac

# ── 2. PUT with test endpoint + model, global flag OFF ───────────────────────
info "${SCENARIO}: PUT update endpoint+model, globalAutoReplyEnabled=false"
http PUT /api/admin/ai \
  '{"endpoint":"http://test-e2e/v1","model":"mock-model-e2e","globalAutoReplyEnabled":false}'
_check "PUT → 200" expect_status 200
_check "endpoint updated" expect_json '.endpoint' "http://test-e2e/v1"
_check "model updated" expect_json '.model' "mock-model-e2e"
put_api_key=$(jq_extract '.apiKey')
case "$put_api_key" in
  ""|"null"|"***"|"sk-***"*)
    pass "${SCENARIO}: apiKey still masked in PUT response"
    ;;
  *)
    if [ "${#put_api_key}" -le 20 ]; then
      pass "${SCENARIO}: apiKey still masked in PUT response (short placeholder)"
    else
      fail "${SCENARIO}: apiKey appears unmasked in PUT response"
      _fail=$((_fail + 1))
    fi
    ;;
esac

# ── 3. GET reflects the PUT ───────────────────────────────────────────────────
info "${SCENARIO}: GET after PUT reflects changes"
http GET /api/admin/ai
_check "GET endpoint reflects PUT" expect_json '.endpoint' "http://test-e2e/v1"
_check "GET model reflects PUT" expect_json '.model' "mock-model-e2e"
_check "GET globalAutoReplyEnabled=false" expect_json '.globalAutoReplyEnabled' "false"

# ── 4. PUT global flag — respect SKIP_AI ─────────────────────────────────────
# When SKIP_AI=1, keep autoReply disabled so AI dispatch doesn't slow down tests
if [ "${SKIP_AI:-}" = "1" ]; then
  info "${SCENARIO}: SKIP_AI=1 — keeping globalAutoReplyEnabled=false"
  http PUT /api/admin/ai '{"endpoint":"","globalAutoReplyEnabled":false}'
  _check "PUT global flag off (SKIP_AI) → 200" expect_status 200
else
  info "${SCENARIO}: PUT global flag back to true"
  http PUT /api/admin/ai '{"globalAutoReplyEnabled":true}'
  _check "PUT global flag back → 200" expect_status 200
  _check "globalAutoReplyEnabled=true" expect_json '.globalAutoReplyEnabled' "true"
fi

# ── Result ────────────────────────────────────────────────────────────────────
info "${SCENARIO}: fail=${_fail}"
[ "$_fail" -eq 0 ]
