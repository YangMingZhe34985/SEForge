-- RULE / MANUAL questions do not require a rubric item. Existing feedback remains unchanged.
ALTER TABLE feedback
    ADD COLUMN question_id BIGINT NULL,
    ADD KEY idx_feedback_question (question_id),
    ADD CONSTRAINT fk_feedback_question FOREIGN KEY (question_id) REFERENCES assignment_question (id) ON DELETE SET NULL;
