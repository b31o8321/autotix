-- SQLite DDL — used by application-sqlite.yml
-- INTEGER PRIMARY KEY AUTOINCREMENT acts as the rowid alias (bigint-compatible)
-- BOOLEAN -> INTEGER (0/1); CLOB -> TEXT; TIMESTAMP -> TEXT (ISO-8601)

CREATE TABLE IF NOT EXISTS ticket (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    channel_id VARCHAR(64) NOT NULL,
    external_native_id VARCHAR(128) NOT NULL,
    subject VARCHAR(512),
    customer_identifier VARCHAR(256),
    customer_name VARCHAR(256),
    status VARCHAR(16) NOT NULL,
    assignee_id VARCHAR(64),
    tags_csv VARCHAR(512),
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    UNIQUE (channel_id, external_native_id)
);
CREATE INDEX IF NOT EXISTS idx_ticket_status_updated ON ticket(status, updated_at);

CREATE TABLE IF NOT EXISTS ticket_message (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    ticket_id INTEGER NOT NULL,
    direction VARCHAR(16) NOT NULL,
    author VARCHAR(128) NOT NULL,
    content TEXT NOT NULL,
    occurred_at TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_msg_ticket ON ticket_message(ticket_id, occurred_at);

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
