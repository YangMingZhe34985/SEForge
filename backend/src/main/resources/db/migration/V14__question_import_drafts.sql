CREATE TABLE question_import_draft (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    assignment_id BIGINT NOT NULL,
    owner_id BIGINT NOT NULL,
    source_media_id BIGINT NOT NULL UNIQUE,
    draft_json LONGTEXT NOT NULL,
    confirmation_json LONGTEXT NULL,
    result_json LONGTEXT NULL,
    CONSTRAINT fk_import_assignment FOREIGN KEY (assignment_id) REFERENCES assignment(id),
    CONSTRAINT fk_import_media FOREIGN KEY (source_media_id) REFERENCES assignment_media(id),
    INDEX idx_import_scope (assignment_id, owner_id)
);
