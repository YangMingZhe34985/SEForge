ALTER TABLE grade ADD COLUMN rule_suggested_score DECIMAL(10,2) NULL;

CREATE TABLE assignment_media (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    assignment_id BIGINT NOT NULL,
    course_id BIGINT NOT NULL,
    owner_id BIGINT NOT NULL,
    question_id BIGINT NULL,
    submission_id BIGINT NULL,
    purpose VARCHAR(24) NOT NULL,
    object_key VARCHAR(512) NOT NULL UNIQUE,
    file_name VARCHAR(180) NOT NULL,
    media_type VARCHAR(100) NOT NULL,
    size_bytes BIGINT NOT NULL,
    CONSTRAINT fk_media_assignment FOREIGN KEY (assignment_id) REFERENCES assignment(id),
    CONSTRAINT fk_media_question FOREIGN KEY (question_id) REFERENCES assignment_question(id),
    CONSTRAINT fk_media_submission FOREIGN KEY (submission_id) REFERENCES submission(id),
    INDEX idx_media_scope (course_id, assignment_id, submission_id),
    INDEX idx_media_owner (owner_id)
);
