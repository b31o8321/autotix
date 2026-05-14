# Email Channel — Admin Guide

Autotix supports a native email channel that polls an IMAP inbox for inbound messages and
sends replies via SMTP. This guide covers setup, configuration, threading behaviour, and
local testing.

---

## Required Credential Fields

When connecting an email channel via `POST /api/admin/channels/connect-api-key`, supply a
`credentials` object with the following fields:

### IMAP (Inbound)

| Field           | Required | Example                 | Description                                      |
|-----------------|----------|-------------------------|--------------------------------------------------|
| `imap_host`     | yes      | `imap.gmail.com`        | IMAP server hostname                             |
| `imap_port`     | yes      | `993`                   | Port (993 = IMAPS, 143 = plain/STARTTLS)         |
| `imap_user`     | yes      | `support@example.com`   | IMAP login username                              |
| `imap_password` | yes      | `secret`                | IMAP login password (app password if 2FA)        |
| `imap_use_ssl`  | no       | `true`                  | `true` for IMAPS; defaults to `false` (plain)    |

### SMTP (Outbound)

| Field              | Required | Example                 | Description                                          |
|--------------------|----------|-------------------------|------------------------------------------------------|
| `smtp_host`        | yes      | `smtp.gmail.com`        | SMTP server hostname                                 |
| `smtp_port`        | yes      | `587`                   | Port (587 = STARTTLS, 465 = SMTPS, 25 = plain)       |
| `smtp_user`        | yes      | `support@example.com`   | SMTP login username                                  |
| `smtp_password`    | yes      | `secret`                | SMTP login password                                  |
| `smtp_use_tls`     | no       | `true`                  | `true` enables STARTTLS; defaults to `false`         |
| `from_address`     | yes      | `support@example.com`   | The `From:` address used on all outbound replies     |

### Minimal Example (Gmail)

```json
{
  "platform": "EMAIL",
  "channelType": "EMAIL",
  "displayName": "Support Inbox",
  "credentials": {
    "imap_host": "imap.gmail.com",
    "imap_port": "993",
    "imap_user": "support@yourcompany.com",
    "imap_password": "your-app-password",
    "imap_use_ssl": "true",
    "smtp_host": "smtp.gmail.com",
    "smtp_port": "587",
    "smtp_user": "support@yourcompany.com",
    "smtp_password": "your-app-password",
    "smtp_use_tls": "true",
    "from_address": "support@yourcompany.com"
  }
}
```

---

## Poll Interval

The IMAP poller runs on a configurable fixed delay:

```yaml
# application.yml (or environment variable override)
autotix:
  email:
    poll-interval-ms: 60000   # default: 60 000 ms (1 minute)
```

To reduce latency, lower the value (e.g. `15000` for 15s). Very short intervals may cause
rate-limiting from commercial mail providers.

Environment variable override (Docker):
```
AUTOTIX_EMAIL_POLL_INTERVAL_MS=30000
```

---

## Threading: Message-ID / In-Reply-To

Autotix uses standard RFC 2822 email threading headers to route replies to the correct
existing ticket:

1. **Inbound new thread**: no `In-Reply-To` header → new ticket created; the email's
   `Message-ID` is stored on the first message row (`email_message_id`).

2. **Inbound reply**: `In-Reply-To: <some-message-id>` present → autotix looks up
   `some-message-id` in `ticket_message.email_message_id`. If found, the new message is
   appended to the matching ticket instead of creating a new one.

3. **Outbound reply**: autotix sets `In-Reply-To` to the last known inbound `Message-ID`
   so mail clients group the conversation thread correctly.

The `email_message_id` column is stored without angle brackets
(`abc@mail.example.com`, not `<abc@mail.example.com>`).

---

## Health Check

The `healthCheck` method (called by `connect-api-key`) briefly opens both an IMAP and SMTP
connection to verify the credentials. Connection timeout is 5 seconds per protocol.

If health check fails, the channel is not enabled and an error is returned to the caller.

---

## Limitations

### OAuth2 / XOAUTH2

Plain-password SMTP/IMAP auth is the only supported mechanism. Gmail and Microsoft 365 both
require app-specific passwords when using plain auth; standard OAuth2 flows (XOAUTH2) are
**not yet implemented**.

### DKIM / SPF

Autotix does not sign outbound emails with DKIM. For production, it is recommended to relay
outbound email through a transactional email service (SendGrid, Postmark, AWS SES) that
handles DKIM/SPF automatically. The `smtp_host` credential can point to such a relay.

### Bounce Handling

Bounce messages (DSN / NDR) delivered to the inbox are processed as regular inbound
messages and will create or update tickets with the bounce body. Dedicated bounce handling
(parsing NDR codes, marking tickets with delivery failure) is **not yet implemented**.

### Spam / Filtering

No built-in spam filter. Enable server-side spam filtering on the mailbox and move spam to
a folder other than INBOX. The poller only reads the INBOX folder.

### Outbound Attachments

Sending attachments in outbound replies is **not yet wired** (TODO comment in EmailPlugin).
The current implementation sends HTML-only replies.

---

## Local Test Setup

### GreenMail (Docker — recommended for E2E tests)

```bash
docker compose --profile mail up -d mail
```

This starts GreenMail with:
- SMTP on port 3025
- IMAP on port 3143
- API on port 8088 (web UI)
- Pre-created users: `agent@autotix.local` and `customer@example.com` (password `secret`)

Connect autotix with:
```json
{
  "imap_host": "localhost", "imap_port": "3143",
  "imap_user": "agent@autotix.local", "imap_password": "secret", "imap_use_ssl": "false",
  "smtp_host": "localhost", "smtp_port": "3025",
  "smtp_user": "agent@autotix.local", "smtp_password": "secret", "smtp_use_tls": "false",
  "from_address": "agent@autotix.local"
}
```

### MailHog (alternative, simpler)

MailHog captures SMTP but does not expose a real IMAP server. Use it for manual outbound
testing only (view replies in the MailHog web UI on port 8025). The autotix IMAP poller
will not work with MailHog for inbound flow.

```bash
docker run -d -p 1025:1025 -p 8025:8025 mailhog/mailhog
```

Then set `smtp_host=localhost`, `smtp_port=1025` in credentials.

---

## Running the E2E Scenario

```bash
# 1. Start GreenMail
docker compose --profile mail up -d mail
sleep 8   # allow GreenMail to fully initialize

# 2. Ensure python3 is available
python3 --version

# 3. Run scenario 18
bash e2e/run.sh 18
```

Expected output: `PASS 18_email_channel` (or `SKIP` if GreenMail is not running or python3
is missing).
