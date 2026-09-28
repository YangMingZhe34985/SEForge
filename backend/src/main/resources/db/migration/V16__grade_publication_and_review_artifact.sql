ALTER TABLE grade ADD COLUMN published_at TIMESTAMP(6) NULL, ADD COLUMN published_by BIGINT NULL,
    ADD CONSTRAINT fk_grade_publisher FOREIGN KEY (published_by) REFERENCES users(id);
-- Previously confirmed grades were already visible: retain that visibility, without changing scores.
UPDATE grade SET status='PUBLISHED', published_at=confirmed_at, published_by=grader_id WHERE status='CONFIRMED';
UPDATE grade SET status='WAITING_REVIEW' WHERE status='PENDING';
UPDATE grade SET status='PENDING_CONFIRMATION' WHERE status IN ('AI_REVIEWED','RULE_REVIEWED');
INSERT INTO grade(submission_id,course_id,student_id,status,created_at,updated_at,version)
SELECT s.id,s.course_id,s.user_id,'WAITING_REVIEW',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),0
FROM submission s WHERE s.status<>'DRAFT' AND NOT EXISTS(SELECT 1 FROM grade g WHERE g.submission_id=s.id);

CREATE TABLE review_artifact (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    course_id BIGINT NOT NULL, owner_id BIGINT NOT NULL,
    object_key VARCHAR(512) NOT NULL, file_name VARCHAR(255) NOT NULL,
    media_type VARCHAR(128) NOT NULL, size_bytes BIGINT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_review_artifact_course FOREIGN KEY(course_id) REFERENCES courses(id),
    CONSTRAINT fk_review_artifact_owner FOREIGN KEY(owner_id) REFERENCES users(id),
    INDEX idx_review_artifact_course(course_id)
);
