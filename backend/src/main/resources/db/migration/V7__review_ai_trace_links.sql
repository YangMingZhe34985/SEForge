ALTER TABLE review_report ADD COLUMN ai_trace_id BIGINT NULL,
    ADD CONSTRAINT fk_review_report_ai_trace FOREIGN KEY (ai_trace_id) REFERENCES ai_trace(id);
ALTER TABLE grade ADD COLUMN ai_trace_id BIGINT NULL,
    ADD CONSTRAINT fk_grade_ai_trace FOREIGN KEY (ai_trace_id) REFERENCES ai_trace(id);
