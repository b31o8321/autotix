-- MySQL DDL — used by application-mysql.yml
-- CLOB -> LONGTEXT; BOOLEAN -> TINYINT(1); TIMESTAMP stays
-- Slice 8: UNIQUE KEY uq_ticket_channel_native dropped; regular index added (channel_id, external_native_id, created_at)
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
    solved_at TIMESTAMP NULL,
    closed_at TIMESTAMP NULL,
    parent_ticket_id BIGINT,
    reopen_count INT NOT NULL DEFAULT 0,
    priority VARCHAR(16) NOT NULL DEFAULT 'NORMAL',
    type VARCHAR(16) NOT NULL DEFAULT 'QUESTION',
    first_response_at TIMESTAMP NULL,
    first_human_response_at TIMESTAMP NULL,
    first_response_due_at TIMESTAMP NULL,
    resolution_due_at TIMESTAMP NULL,
    sla_breached TINYINT(1) NOT NULL DEFAULT 0,
    customer_id BIGINT,
    ai_suspended TINYINT(1) NOT NULL DEFAULT 0,
    escalated_at TIMESTAMP NULL,
    custom_fields_json LONGTEXT
);
CREATE INDEX idx_ticket_channel_native_created ON ticket(channel_id, external_native_id, created_at);
CREATE INDEX idx_ticket_customer ON ticket(customer_id);
CREATE INDEX idx_ticket_status_updated ON ticket(status, updated_at);
CREATE INDEX idx_ticket_status_solved ON ticket(status, solved_at);

CREATE TABLE IF NOT EXISTS ticket_message (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    ticket_id BIGINT NOT NULL,
    direction VARCHAR(16) NOT NULL,
    author VARCHAR(128) NOT NULL,
    content LONGTEXT NOT NULL,
    occurred_at TIMESTAMP NOT NULL,
    visibility VARCHAR(16) NOT NULL DEFAULT 'PUBLIC'
);
CREATE INDEX idx_msg_ticket ON ticket_message(ticket_id, occurred_at);

CREATE TABLE IF NOT EXISTS ticket_activity (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    ticket_id BIGINT NOT NULL,
    actor VARCHAR(128) NOT NULL,
    action VARCHAR(32) NOT NULL,
    details LONGTEXT,
    occurred_at TIMESTAMP NOT NULL
);
CREATE INDEX idx_activity_ticket_occurred ON ticket_activity(ticket_id, occurred_at);

CREATE TABLE IF NOT EXISTS channel (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    platform VARCHAR(32) NOT NULL,
    channel_type VARCHAR(16) NOT NULL,
    display_name VARCHAR(256) NOT NULL,
    webhook_token VARCHAR(128) NOT NULL UNIQUE,
    access_token LONGTEXT,
    refresh_token LONGTEXT,
    expires_at TIMESTAMP NULL,
    attributes_json LONGTEXT,
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    auto_reply_enabled TINYINT(1) NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    UNIQUE KEY uq_channel_platform_token (platform, webhook_token)
);

CREATE TABLE IF NOT EXISTS app_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(256) NOT NULL UNIQUE,
    display_name VARCHAR(128) NOT NULL,
    password_hash VARCHAR(256) NOT NULL,
    role VARCHAR(16) NOT NULL,
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    last_login_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS automation_rule (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(256) NOT NULL,
    priority INT NOT NULL DEFAULT 100,
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    conditions_json LONGTEXT,
    actions_json LONGTEXT,
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
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

-- Singleton AI config row (id always = 1)
CREATE TABLE IF NOT EXISTS ai_config (
    id BIGINT PRIMARY KEY,
    endpoint VARCHAR(512) NOT NULL,
    api_key VARCHAR(512),
    model VARCHAR(128) NOT NULL,
    system_prompt LONGTEXT,
    timeout_seconds INT,
    max_retries INT,
    updated_at TIMESTAMP NOT NULL
);

-- Slice 12: customer aggregate
CREATE TABLE IF NOT EXISTS customer (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    display_name VARCHAR(256),
    primary_email VARCHAR(256),
    attributes_json LONGTEXT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);
CREATE INDEX idx_customer_email ON customer(primary_email);

CREATE TABLE IF NOT EXISTS customer_identifier (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    customer_id BIGINT NOT NULL,
    identifier_type VARCHAR(32) NOT NULL,
    identifier_value VARCHAR(512) NOT NULL,
    channel_id VARCHAR(64),
    first_seen_at TIMESTAMP NOT NULL,
    UNIQUE KEY uq_customer_identifier_type_value (identifier_type, identifier_value)
);
CREATE INDEX idx_customer_identifier_customer ON customer_identifier(customer_id);

-- Slice 12: tag definition
CREATE TABLE IF NOT EXISTS tag_definition (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(128) NOT NULL UNIQUE,
    color VARCHAR(16) NOT NULL DEFAULT '#9BAAB8',
    category VARCHAR(64),
    created_at TIMESTAMP NOT NULL
);

-- Slice 12: custom field definition
CREATE TABLE IF NOT EXISTS custom_field_definition (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    field_key VARCHAR(64) NOT NULL UNIQUE,
    field_type VARCHAR(16) NOT NULL,
    applies_to VARCHAR(16) NOT NULL,
    required TINYINT(1) NOT NULL DEFAULT 0,
    display_order INT NOT NULL DEFAULT 100,
    created_at TIMESTAMP NOT NULL
);

-- Slice 11: file attachments
CREATE TABLE IF NOT EXISTS attachment (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    message_id BIGINT,
    ticket_id BIGINT NOT NULL,
    storage_key VARCHAR(512) NOT NULL UNIQUE,
    file_name VARCHAR(256) NOT NULL,
    content_type VARCHAR(128) NOT NULL,
    size_bytes BIGINT NOT NULL,
    uploaded_by VARCHAR(128) NOT NULL,
    uploaded_at TIMESTAMP NOT NULL
);
CREATE INDEX idx_attachment_ticket ON attachment(ticket_id, uploaded_at);
