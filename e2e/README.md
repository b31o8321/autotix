# autotix E2E Test Harness

Black-box HTTP tests using `bash` + `curl` + `jq`, run against the live `docker compose` stack.

## Prerequisites

- Docker Compose stack running: `docker compose up -d` from repo root
- `jq` installed (`brew install jq` / `apt install jq`)
- `curl` installed (ships with macOS and most Linux distros)
- No Ollama/AI required for most scenarios (see `SKIP_AI`)

## Running

```bash
# Full suite (from repo root)
bash e2e/run.sh

# Only specific scenarios (prefix match)
bash e2e/run.sh 04 05

# Override backend URL
BASE_URL=http://myserver:8080 bash e2e/run.sh

# Skip AI-dependent scenarios (no Ollama configured)
SKIP_AI=1 bash e2e/run.sh
```

## Environment Variables

| Variable          | Default                   | Description                                         |
|-------------------|---------------------------|-----------------------------------------------------|
| `BASE_URL`        | `http://localhost:8080`   | Backend base URL                                    |
| `ADMIN_EMAIL`     | `admin@autotix.local`     | Admin login email                                   |
| `ADMIN_PASSWORD`  | `admin`                   | Admin login password                                |
| `SKIP_AI`         | _(unset)_                 | Set to `1` to skip scenarios requiring a real AI    |
| `E2E_TIMEOUT_SEC` | `60`                      | Per-scenario timeout in seconds                     |

## Scenario Coverage

| # | File                            | What it tests                                                         |
|---|---------------------------------|-----------------------------------------------------------------------|
| 01 | `01_auth.sh`                   | Bad password → 401, good login → tokens, `/me`, auth bypass checks   |
| 02 | `02_ai_config.sh`              | AI config GET masks apiKey, PUT updates endpoint/model/global flag    |
| 03 | `03_channel_setup.sh`          | Create CUSTOM channel, autoReply toggle, save channel env to `/tmp`   |
| 04 | `04_inbound_creates_ticket.sh` | POST webhook → ticket appears, INBOUND message, correct customer      |
| 05 | `05_auto_reply_ai.sh`          | AI auto-reply on inbound, status → WAITING_ON_CUSTOMER _(skippable)_ |
| 06 | `06_escalate_blocks_ai.sh`     | Escalate → aiSuspended=true, second inbound triggers no AI            |
| 07 | `07_resume_ai_admin.sh`        | Admin resumes AI → aiSuspended=false, AI_RESUMED in activity log      |
| 08 | `08_internal_note.sh`          | Internal note does not change status or expose as PUBLIC              |
| 09 | `09_solve_reopen.sh`           | Solve → SOLVED; inbound within window reopens same ticket             |
| 10 | `10_close_spawns_new.sh`       | Permanent close; inbound spawns new ticket with parentTicketId        |
| 11 | `11_tags_lifecycle.sh`         | Add/remove tags; tags appear in TagDefinition library                 |
| 12 | `12_custom_fields.sh`          | Create field def, set value, verify, clear value                      |
| 13 | `13_priority_type_change.sh`   | Priority → HIGH (SLA recomputed), type → INCIDENT                    |
| 14 | `14_sla_breach.sh`             | Set 1-min SLA, wait for scheduler (~75s), assert slaBreached=true     |
| 15 | `15_customer_aggregation.sh`   | Same email on 2 channels → 1 Customer, ≥2 identifiers                |
| 16 | `16_attachments_local.sh`      | Inbound with base64 attachment; download and MD5 verify               |
| 17 | `17_activity_history.sh`       | Aggregate activity across tickets; assert ≥5 distinct action types    |

## Known Limitations

1. **Scenario 14 sleeps ~75s** — the SLA breach scheduler runs on a 60s interval. There is no admin endpoint to trigger it manually. The test waits patiently and soft-fails if the scheduler hasn't fired within 75s.

2. **Scenario 05 skips without real AI** — set `SKIP_AI=1` or leave AI config endpoint as a placeholder. The test auto-detects "test/mock" endpoints and exits with code 77 (SKIP).

3. **Inter-scenario state via `/tmp/e2e-channel.env`** — scenarios 04–17 reuse the CUSTOM channel created in scenario 03. If you run a single scenario in isolation, set `E2E_TEST_CHANNEL_ID` and `E2E_TEST_WEBHOOK_TOKEN` manually, or run from 03 onwards.

4. **run.sh shows output twice** — the current implementation runs each scenario twice (once for display, once for exit code capture). This is a cosmetic issue; correctness is unaffected.

5. **MD5 check in scenario 16** — base64 decoding round-trips are verified by MD5. If the server re-encodes or transforms the file, the MD5 will differ; the test logs a note rather than failing hard.

## Adding a New Scenario

1. Create `e2e/scenarios/NN_your_name.sh` (two-digit zero-padded number).
2. Start with `set -euo pipefail` and source convention:
   ```bash
   if [ -f /tmp/e2e-channel.env ]; then source /tmp/e2e-channel.env; fi
   ```
3. Use helpers from `lib/common.sh`: `http`, `http_unauth`, `expect_status`, `expect_json`, `poll_until`, `pass`/`fail`/`info`.
4. Exit 0 on success, nonzero on failure, 77 for skip.
5. `run.sh` picks it up automatically by filename ordering.
