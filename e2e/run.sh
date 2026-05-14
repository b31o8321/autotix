#!/usr/bin/env bash
# run.sh — autotix E2E test orchestrator
#
# Usage:
#   bash e2e/run.sh                  # full suite
#   bash e2e/run.sh 04 05            # only those scenario numbers (prefix match)
#   BASE_URL=... bash e2e/run.sh     # override default http://localhost:8080
#
# Env vars:
#   BASE_URL           default http://localhost:8080
#   ADMIN_EMAIL        default admin@autotix.local
#   ADMIN_PASSWORD     default admin
#   SKIP_AI            if set to 1, scenarios requiring a real AI model are skipped
#   E2E_TIMEOUT_SEC    per-scenario timeout, default 60

set -euo pipefail

# ── Resolve script directory ──────────────────────────────────────────────────
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

# ── Defaults ──────────────────────────────────────────────────────────────────
export BASE_URL="${BASE_URL:-http://localhost:8080}"
export ADMIN_EMAIL="${ADMIN_EMAIL:-admin@autotix.local}"
export ADMIN_PASSWORD="${ADMIN_PASSWORD:-admin}"
export SKIP_AI="${SKIP_AI:-}"
E2E_TIMEOUT_SEC="${E2E_TIMEOUT_SEC:-180}"

# ── Dependency check ──────────────────────────────────────────────────────────
if ! command -v jq >/dev/null 2>&1; then
  echo "ERROR: 'jq' is not installed or not in PATH."
  echo "  macOS:  brew install jq"
  echo "  Debian: apt-get install jq"
  echo "  RHEL:   yum install jq"
  exit 1
fi

if ! command -v curl >/dev/null 2>&1; then
  echo "ERROR: 'curl' is not installed or not in PATH."
  exit 1
fi

# ── Source helpers ────────────────────────────────────────────────────────────
# shellcheck source=lib/common.sh
source "${SCRIPT_DIR}/lib/common.sh"
# shellcheck source=lib/login.sh
source "${SCRIPT_DIR}/lib/login.sh"

# ── wait_for_backend ──────────────────────────────────────────────────────────
wait_for_backend() {
  info "Waiting for backend at ${BASE_URL} (up to 30s) ..."
  local attempts=0
  while [ "$attempts" -lt 15 ]; do
    local status
    status=$(curl -s -o /dev/null -w "%{http_code}" \
      "${BASE_URL}/api/auth/me" 2>/dev/null || true)
    # 401/200/500 = backend up, 000 = not reachable
    if [ "$status" = "401" ] || [ "$status" = "200" ] || [ "$status" = "500" ]; then
      pass "Backend reachable (HTTP $status)"
      return 0
    fi
    sleep 2
    attempts=$((attempts + 1))
  done
  fail "Backend did not become reachable within 30s at ${BASE_URL}"
  exit 1
}

# ── Collect scenario files ────────────────────────────────────────────────────
SCENARIO_DIR="${SCRIPT_DIR}/scenarios"

# Build list of scenarios matching optional prefix filters (args)
all_scenarios=()
for f in "${SCENARIO_DIR}"/[0-9][0-9]_*.sh; do
  [ -f "$f" ] && all_scenarios+=("$f")
done

if [ ${#all_scenarios[@]} -eq 0 ]; then
  fail "No scenario files found in ${SCENARIO_DIR}"
  exit 1
fi

# Filter by prefixes if args provided
selected_scenarios=()
if [ $# -gt 0 ]; then
  for f in "${all_scenarios[@]}"; do
    base=$(basename "$f")
    for prefix in "$@"; do
      case "$base" in
        "${prefix}"_* | "${prefix}"[^0-9]*)
          selected_scenarios+=("$f")
          break
          ;;
      esac
    done
  done
else
  selected_scenarios=("${all_scenarios[@]}")
fi

if [ ${#selected_scenarios[@]} -eq 0 ]; then
  fail "No scenarios matched the given filter(s): $*"
  exit 1
fi

# ── Bootstrap ─────────────────────────────────────────────────────────────────
printf "\n${C_BOLD}autotix E2E — %d scenario(s)${C_RESET}\n" "${#selected_scenarios[@]}"
printf "  BASE_URL   : %s\n" "${BASE_URL}"
printf "  ADMIN      : %s\n" "${ADMIN_EMAIL}"
printf "  SKIP_AI    : %s\n" "${SKIP_AI:-<not set>}"
printf "\n"

wait_for_backend
login_admin
printf "\n"

# ── Run scenarios ─────────────────────────────────────────────────────────────
pass_count=0
fail_count=0
skip_count=0
declare -a results=()

for scenario in "${selected_scenarios[@]}"; do
  name=$(basename "$scenario" .sh)
  printf "${C_BOLD}── %s ──${C_RESET}\n" "$name"

  t_start=$(date +%s)

  # Run scenario in a subshell so failures don't exit the orchestrator.
  # Capture output and exit code in one pass.
  exit_code=0
  TMP_OUT=$(mktemp)

  _run_scenario() {
    source "${SCRIPT_DIR}/lib/common.sh"
    export ADMIN_TOKEN="${ADMIN_TOKEN}"
    export BASE_URL="${BASE_URL}"
    export SKIP_AI="${SKIP_AI:-}"
    # shellcheck disable=SC1090
    source "$scenario"
  }

  _scenario_env="
    source '${SCRIPT_DIR}/lib/common.sh'
    export ADMIN_TOKEN='${ADMIN_TOKEN}'
    export BASE_URL='${BASE_URL}'
    export ADMIN_EMAIL='${ADMIN_EMAIL}'
    export ADMIN_PASSWORD='${ADMIN_PASSWORD}'
    export SKIP_AI='${SKIP_AI:-}'
    source '${scenario}'
  "
  TMP_EC=$(mktemp)
  # Disable set -e locally to capture non-zero exit without exiting orchestrator
  set +e
  if command -v timeout >/dev/null 2>&1; then
    ( timeout "${E2E_TIMEOUT_SEC}" bash -c "${_scenario_env}" ) > "${TMP_OUT}" 2>&1
  else
    ( bash -c "${_scenario_env}" ) > "${TMP_OUT}" 2>&1
  fi
  exit_code=$?
  set -e
  rm -f "${TMP_EC}"

  # Print captured output indented
  sed 's/^/  /' "${TMP_OUT}"
  rm -f "${TMP_OUT}"

  t_end=$(date +%s)
  elapsed=$((t_end - t_start))

  if [ "$exit_code" -eq 0 ]; then
    printf "  ${C_GREEN}PASS${C_RESET} (%ds)\n\n" "$elapsed"
    pass_count=$((pass_count + 1))
    results+=("PASS | ${elapsed}s | ${name}")
  elif [ "$exit_code" -eq 77 ]; then
    # Convention: exit 77 = SKIP
    printf "  ${C_YELLOW}SKIP${C_RESET} (%ds)\n\n" "$elapsed"
    skip_count=$((skip_count + 1))
    results+=("SKIP | ${elapsed}s | ${name}")
  else
    printf "  ${C_RED}FAIL${C_RESET} (exit=%d, %ds)\n\n" "$exit_code" "$elapsed"
    fail_count=$((fail_count + 1))
    results+=("FAIL | ${elapsed}s | ${name}")
  fi
done

# ── Summary table ─────────────────────────────────────────────────────────────
printf "${C_BOLD}═══════════════════════════════════════════════════════════${C_RESET}\n"
printf "${C_BOLD}  SUMMARY${C_RESET}\n"
printf "${C_BOLD}═══════════════════════════════════════════════════════════${C_RESET}\n"
printf "  %-6s  %-5s  %s\n" "Result" "Time" "Scenario"
printf "  %-6s  %-5s  %s\n" "------" "-----" "--------"
for r in "${results[@]}"; do
  result=$(printf '%s' "$r" | cut -d'|' -f1 | tr -d ' ')
  elapsed_f=$(printf '%s' "$r" | cut -d'|' -f2 | tr -d ' ')
  scenario_f=$(printf '%s' "$r" | cut -d'|' -f3 | tr -d ' ')
  case "$result" in
    PASS) color="${C_GREEN}" ;;
    SKIP) color="${C_YELLOW}" ;;
    FAIL) color="${C_RED}" ;;
    *) color="${C_RESET}" ;;
  esac
  printf "  ${color}%-6s${C_RESET}  %-5s  %s\n" "$result" "$elapsed_f" "$scenario_f"
done
printf "${C_BOLD}═══════════════════════════════════════════════════════════${C_RESET}\n"
printf "  ${C_GREEN}PASS: %d${C_RESET}  ${C_YELLOW}SKIP: %d${C_RESET}  ${C_RED}FAIL: %d${C_RESET}  TOTAL: %d\n" \
  "$pass_count" "$skip_count" "$fail_count" "${#selected_scenarios[@]}"
printf "${C_BOLD}═══════════════════════════════════════════════════════════${C_RESET}\n\n"

if [ "$fail_count" -gt 0 ]; then
  exit 1
fi
exit 0
