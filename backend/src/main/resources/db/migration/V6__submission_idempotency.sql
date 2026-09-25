ALTER TABLE submission
    ADD COLUMN submission_key VARCHAR(64) NULL AFTER attempt_no,
    ADD UNIQUE KEY uk_submission_request (assignment_id, user_id, submission_key);
