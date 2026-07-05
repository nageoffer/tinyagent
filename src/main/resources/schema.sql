CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS chat_session (
    session_id  VARCHAR(64)  PRIMARY KEY,
    user_id     VARCHAR(64)  NOT NULL,
    title       VARCHAR(256),
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_chat_session_user_id ON chat_session(user_id);

CREATE TABLE IF NOT EXISTS chat_message (
    id           BIGSERIAL    PRIMARY KEY,
    session_id   VARCHAR(64)  NOT NULL,
    role         VARCHAR(16)  NOT NULL,
    content      TEXT,
    tool_call_id VARCHAR(64),
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_chat_message_session_id ON chat_message(session_id);

CREATE TABLE IF NOT EXISTS memory_entry (
    key         VARCHAR(256) PRIMARY KEY,
    content     TEXT         NOT NULL,
    user_id     VARCHAR(64)  NOT NULL,
    type        VARCHAR(32)  NOT NULL,
    embedding   vector(1024),
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_memory_entry_user_id ON memory_entry(user_id);
