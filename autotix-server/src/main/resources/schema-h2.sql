-- H2 DDL — used by application-h2.yml (dev/test profile)
-- Slice 8: new status values (NEW/OPEN/WAITING_ON_CUSTOMER/WAITING_ON_INTERNAL/SOLVED/CLOSED/SPAM)
--   Old values: PENDING → WAITING_ON_CUSTOMER, ASSIGNED → OPEN (dev DB is wiped on restart)
-- Slice 8: UNIQUE(channel_id, external_native_id) dropped; replaced with regular index
--   including created_at for most-recent lookup ORDER BY created_at DESC LIMIT 1
-- Slice 8: new columns: solved_at, closed_at, parent_ticket_id, reopen_count

CREATE TABLE IF NOT EXISTS ticket (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    channel_id VARCHAR(64) NOT NULL,
    external_native_id VARCHAR(128) NOT NULL,
    subject VARCHAR(512),
    customer_identifier VARCHAR(256),
    customer_name VARCHAR(256),
    status VARCHAR(32) NOT NULL,
    assignee_id VARCHAR(64),
    tags_csv VARCHAR(512),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    solved_at TIMESTAMP,
    closed_at TIMESTAMP,
    parent_ticket_id BIGINT,
    reopen_count INT NOT NULL DEFAULT 0,
    priority VARCHAR(16) NOT NULL DEFAULT 'NORMAL',
    type VARCHAR(16) NOT NULL DEFAULT 'QUESTION',
    first_response_at TIMESTAMP,
    first_human_response_at TIMESTAMP,
    first_response_due_at TIMESTAMP,
    resolution_due_at TIMESTAMP,
    sla_breached BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX IF NOT EXISTS idx_ticket_channel_native_created ON ticket(channel_id, external_native_id, created_at);
CREATE INDEX IF NOT EXISTS idx_ticket_status_updated ON ticket(status, updated_at);
CREATE INDEX IF NOT EXISTS idx_ticket_status_solved ON ticket(status, solved_at);

CREATE TABLE IF NOT EXISTS ticket_message (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    ticket_id BIGINT NOT NULL,
    direction VARCHAR(16) NOT NULL,
    author VARCHAR(128) NOT NULL,
    content CLOB NOT NULL,
    occurred_at TIMESTAMP NOT NULL,
    visibility VARCHAR(16) NOT NULL DEFAULT 'PUBLIC'
);
CREATE INDEX IF NOT EXISTS idx_msg_ticket ON ticket_message(ticket_id, occurred_at);

CREATE TABLE IF NOT EXISTS ticket_activity (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    ticket_id BIGINT NOT NULL,
    actor VARCHAR(128) NOT NULL,
    action VARCHAR(32) NOT NULL,
    details CLOB,
    occurred_at TIMESTAMP NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_activity_ticket_occurred ON ticket_activity(ticket_id, occurred_at);

CREATE TABLE IF NOT EXISTS channel (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    platform VARCHAR(32) NOT NULL,
    channel_type VARCHAR(16) NOT NULL,
    display_name VARCHAR(256) NOT NULL,
    webhook_token VARCHAR(128) NOT NULL UNIQUE,
    access_token CLOB,
    refresh_token CLOB,
    expires_at TIMESTAMP,
    attributes_json CLOB,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    auto_reply_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    UNIQUE (platform, webhook_token)
);

-- TODO: user table — see domain/user
CREATE TABLE IF NOT EXISTS app_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(256) NOT NULL UNIQUE,
    display_name VARCHAR(128) NOT NULL,
    password_hash VARCHAR(256) NOT NULL,
    role VARCHAR(16) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    last_login_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS automation_rule (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(256) NOT NULL,
    priority INT NOT NULL DEFAULT 100,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    conditions_json CLOB,
    actions_json CLOB,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

-- Slice 10: SLA policy table (one row per TicketPriority)
CREATE TABLE IF NOT EXISTS sla_policy (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    priority VARCHAR(16) NOT NULL UNIQUE,
    first_response_minutes INT NOT NULL,
    resolution_minutes INT NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

-- Singleton AI config row (id always = 1)
CREATE TABLE IF NOT EXISTS ai_config (
    id BIGINT PRIMARY KEY,
    endpoint VARCHAR(512) NOT NULL,
    api_key VARCHAR(512),
    model VARCHAR(128) NOT NULL,
    system_prompt CLOB,
    timeout_seconds INT,
    max_retries INT,
    updated_at TIMESTAMP NOT NULL
);
