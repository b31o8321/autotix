-- SQLite DDL — used by application-sqlite.yml
-- INTEGER PRIMARY KEY AUTOINCREMENT acts as the rowid alias (bigint-compatible)
-- BOOLEAN -> INTEGER (0/1); CLOB -> TEXT; TIMESTAMP -> TEXT (ISO-8601)
-- Slice 8: UNIQUE(channel_id, external_native_id) dropped; regular index added
-- Slice 8: new columns: solved_at, closed_at, parent_ticket_id, reopen_count

CREATE TABLE IF NOT EXISTS ticket (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    channel_id VARCHAR(64) NOT NULL,
    external_native_id VARCHAR(128) NOT NULL,
    subject VARCHAR(512),
    customer_identifier VARCHAR(256),
    customer_name VARCHAR(256),
    status VARCHAR(32) NOT NULL,
    assignee_id VARCHAR(64),
    tags_csv VARCHAR(512),
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    solved_at TEXT,
    closed_at TEXT,
    parent_ticket_id INTEGER,
    reopen_count INTEGER NOT NULL DEFAULT 0,
    priority VARCHAR(16) NOT NULL DEFAULT 'NORMAL',
    type VARCHAR(16) NOT NULL DEFAULT 'QUESTION',
    first_response_at TEXT,
    first_human_response_at TEXT,
    first_response_due_at TEXT,
    resolution_due_at TEXT,
    sla_breached INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_ticket_channel_native_created ON ticket(channel_id, external_native_id, created_at);
CREATE INDEX IF NOT EXISTS idx_ticket_status_updated ON ticket(status, updated_at);
CREATE INDEX IF NOT EXISTS idx_ticket_status_solved ON ticket(status, solved_at);

CREATE TABLE IF NOT EXISTS ticket_message (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    ticket_id INTEGER NOT NULL,
    direction VARCHAR(16) NOT NULL,
    author VARCHAR(128) NOT NULL,
    content TEXT NOT NULL,
    occurred_at TEXT NOT NULL,
    visibility VARCHAR(16) NOT NULL DEFAULT 'PUBLIC'
);
CREATE INDEX IF NOT EXISTS idx_msg_ticket ON ticket_message(ticket_id, occurred_at);

CREATE TABLE IF NOT EXISTS ticket_activity (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    ticket_id INTEGER NOT NULL,
    actor VARCHAR(128) NOT NULL,
    action VARCHAR(32) NOT NULL,
    details TEXT,
    occurred_at TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_activity_ticket_occurred ON ticket_activity(ticket_id, occurred_at);

CREATE TABLE IF NOT EXISTS channel (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    platform VARCHAR(32) NOT NULL,
    channel_type VARCHAR(16) NOT NULL,
    display_name VARCHAR(256) NOT NULL,
    webhook_token VARCHAR(128) NOT NULL UNIQUE,
    access_token TEXT,
    refresh_token TEXT,
    expires_at TEXT,
    attributes_json TEXT,
    enabled INTEGER NOT NULL DEFAULT 1,
    auto_reply_enabled INTEGER NOT NULL DEFAULT 0,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    UNIQUE (platform, webhook_token)
);

CREATE TABLE IF NOT EXISTS app_user (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    email VARCHAR(256) NOT NULL UNIQUE,
    display_name VARCHAR(128) NOT NULL,
    password_hash VARCHAR(256) NOT NULL,
    role VARCHAR(16) NOT NULL,
    enabled INTEGER NOT NULL DEFAULT 1,
    last_login_at TEXT,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS automation_rule (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name VARCHAR(256) NOT NULL,
    priority INTEGER NOT NULL DEFAULT 100,
    enabled INTEGER NOT NULL DEFAULT 1,
    conditions_json TEXT,
    actions_json TEXT,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL
);

-- Slice 10: SLA policy table (one row per TicketPriority)
CREATE TABLE IF NOT EXISTS sla_policy (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name VARCHAR(128) NOT NULL,
    priority VARCHAR(16) NOT NULL UNIQUE,
    first_response_minutes INTEGER NOT NULL,
    resolution_minutes INTEGER NOT NULL,
    enabled INTEGER NOT NULL DEFAULT 1,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL
);

-- Singleton AI config row (id always = 1)
CREATE TABLE IF NOT EXISTS ai_config (
    id INTEGER PRIMARY KEY,
    endpoint VARCHAR(512) NOT NULL,
    api_key VARCHAR(512),
    model VARCHAR(128) NOT NULL,
    system_prompt TEXT,
    timeout_seconds INTEGER,
    max_retries INTEGER,
    updated_at TEXT NOT NULL
);

-- Slice 11: file attachments
CREATE TABLE IF NOT EXISTS attachment (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    message_id INTEGER,
    ticket_id INTEGER NOT NULL,
    storage_key VARCHAR(512) NOT NULL UNIQUE,
    file_name VARCHAR(256) NOT NULL,
    content_type VARCHAR(128) NOT NULL,
    size_bytes INTEGER NOT NULL,
    uploaded_by VARCHAR(128) NOT NULL,
    uploaded_at TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_attachment_ticket ON attachment(ticket_id, uploaded_at);
