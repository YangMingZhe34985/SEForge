-- A retry after a lost HTTP response must not generate another paid Tutor answer.
CREATE TABLE tutor_request_replay (
    user_id BIGINT NOT NULL,
    request_key VARCHAR(36) NOT NULL,
    assignment_id BIGINT NOT NULL,
    request_hash CHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL,
    response_json JSON NULL,
    error_code VARCHAR(64) NULL,
    error_message VARCHAR(500) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (user_id, request_key),
    CONSTRAINT fk_tutor_replay_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_tutor_replay_assignment FOREIGN KEY (assignment_id) REFERENCES assignment(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
