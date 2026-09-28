CREATE TABLE conversation_generation (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    conversation_id BIGINT NOT NULL,
    request_id VARCHAR(64) NOT NULL,
    trace_id VARCHAR(64) NOT NULL,
    question TEXT NOT NULL,
    status VARCHAR(24) NOT NULL,
    deadline_at DATETIME(6) NOT NULL,
    assistant_message_id BIGINT NULL,
    error_code VARCHAR(64) NULL,
    error_message VARCHAR(1000) NULL,
    CONSTRAINT uk_generation_request UNIQUE (conversation_id, request_id),
    CONSTRAINT fk_generation_conversation FOREIGN KEY (conversation_id) REFERENCES conversation(id),
    CONSTRAINT fk_generation_message FOREIGN KEY (assistant_message_id) REFERENCES conversation_message(id),
    INDEX idx_generation_expiry (status, deadline_at)
);
