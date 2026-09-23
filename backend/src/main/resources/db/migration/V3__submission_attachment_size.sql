ALTER TABLE submission_answer
    ADD COLUMN attachment_size_bytes BIGINT NOT NULL DEFAULT 0 AFTER attachment_object_key;
