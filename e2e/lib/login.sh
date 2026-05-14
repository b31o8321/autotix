#!/usr/bin/env bash
# lib/login.sh — login helper
# Requires: BASE_URL, ADMIN_EMAIL, ADMIN_PASSWORD already exported.
# Exports:   ADMIN_TOKEN (access token)

login_admin() {
  local email="${ADMIN_EMAIL:-admin@autotix.local}"
  local password="${ADMIN_PASSWORD:-admin}"

  info "Logging in as ${email} ..."

  http_unauth POST /api/auth/login \
    "{\"email\":\"${email}\",\"password\":\"${password}\"}"

  if ! expect_status 200; then
    fail "Login failed — cannot continue. Body: ${LAST_HTTP_BODY}"
    exit 1
  fi

  ADMIN_TOKEN=$(jq_extract '.accessToken')
  if [ -z "$ADMIN_TOKEN" ] || [ "$ADMIN_TOKEN" = "null" ]; then
    fail "Login response did not contain accessToken. Body: ${LAST_HTTP_BODY}"
    exit 1
  fi

  export ADMIN_TOKEN
  pass "Logged in, token acquired (${#ADMIN_TOKEN} chars)"
}
