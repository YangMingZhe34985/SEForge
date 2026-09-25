-- Durable, transactional change journal. Receipts use applied flags, not MAX(id):
-- AUTO_INCREMENT order is not commit order. No sensitive content is copied here.
CREATE TABLE analytics_change (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, course_id BIGINT NOT NULL,
 source_type VARCHAR(40) NOT NULL, source_id BIGINT NOT NULL,
 applied BOOLEAN NOT NULL DEFAULT FALSE, created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 KEY idx_analytics_pending(course_id,applied,id),
 FOREIGN KEY(course_id) REFERENCES courses(id) ON DELETE CASCADE
);
CREATE TABLE analytics_projection (
 course_id BIGINT PRIMARY KEY, revision BIGINT NOT NULL DEFAULT 0,
 FOREIGN KEY(course_id) REFERENCES courses(id) ON DELETE CASCADE
);
CREATE TABLE analytics_contribution (
 course_id BIGINT NOT NULL, source_type VARCHAR(40) NOT NULL, source_id BIGINT NOT NULL,
 payload JSON NOT NULL, PRIMARY KEY(course_id,source_type,source_id),
 FOREIGN KEY(course_id) REFERENCES courses(id) ON DELETE CASCADE
);
CREATE TABLE analytics_total (
 course_id BIGINT NOT NULL, scope_id BIGINT NOT NULL, metric VARCHAR(32) NOT NULL,
 dimension_key VARCHAR(192) NOT NULL, amount DECIMAL(30,8) NOT NULL,
 PRIMARY KEY(course_id,scope_id,metric,dimension_key),
 FOREIGN KEY(course_id) REFERENCES courses(id) ON DELETE CASCADE
);
INSERT INTO analytics_change(course_id,source_type,source_id) SELECT id,'BOOTSTRAP',id FROM courses;
DELIMITER $$
CREATE TRIGGER analytics_course_insert AFTER INSERT ON courses FOR EACH ROW
BEGIN
 INSERT INTO analytics_change(course_id,source_type,source_id) VALUES(NEW.id,'BOOTSTRAP',NEW.id);
END$$
CREATE TRIGGER an_course_classes_insert AFTER INSERT ON course_classes FOR EACH ROW
BEGIN
 IF NEW.course_id IS NOT NULL THEN
  INSERT INTO analytics_change(course_id,source_type,source_id) VALUES(NEW.course_id,'course_classes',NEW.id);
 END IF;
END$$
CREATE TRIGGER an_course_classes_update AFTER UPDATE ON course_classes FOR EACH ROW
BEGIN
 IF OLD.course_id IS NOT NULL THEN
  INSERT INTO analytics_change(course_id,source_type,source_id) VALUES(OLD.course_id,'course_classes',OLD.id);
 END IF;
 IF NEW.course_id IS NOT NULL THEN
  INSERT INTO analytics_change(course_id,source_type,source_id) VALUES(NEW.course_id,'course_classes',NEW.id);
 END IF;
END$$
CREATE TRIGGER an_course_classes_delete BEFORE DELETE ON course_classes FOR EACH ROW
BEGIN
 IF OLD.course_id IS NOT NULL THEN
  INSERT INTO analytics_change(course_id,source_type,source_id) VALUES(OLD.course_id,'course_classes',OLD.id);
 END IF;
END$$
CREATE TRIGGER an_course_members_insert AFTER INSERT ON course_members FOR EACH ROW
BEGIN
 IF NEW.course_id IS NOT NULL THEN
  INSERT INTO analytics_change(course_id,source_type,source_id) VALUES(NEW.course_id,'course_members',NEW.id);
 END IF;
END$$
CREATE TRIGGER an_course_members_update AFTER UPDATE ON course_members FOR EACH ROW
BEGIN
 IF OLD.course_id IS NOT NULL THEN
  INSERT INTO analytics_change(course_id,source_type,source_id) VALUES(OLD.course_id,'course_members',OLD.id);
 END IF;
 IF NEW.course_id IS NOT NULL THEN
  INSERT INTO analytics_change(course_id,source_type,source_id) VALUES(NEW.course_id,'course_members',NEW.id);
 END IF;
END$$
CREATE TRIGGER an_course_members_delete BEFORE DELETE ON course_members FOR EACH ROW
BEGIN
 IF OLD.course_id IS NOT NULL THEN
  INSERT INTO analytics_change(course_id,source_type,source_id) VALUES(OLD.course_id,'course_members',OLD.id);
 END IF;
END$$
CREATE TRIGGER an_assignment_insert AFTER INSERT ON assignment FOR EACH ROW
BEGIN
 IF NEW.course_id IS NOT NULL THEN
  INSERT INTO analytics_change(course_id,source_type,source_id) VALUES(NEW.course_id,'assignment',NEW.id);
 END IF;
END$$
CREATE TRIGGER an_assignment_update AFTER UPDATE ON assignment FOR EACH ROW
BEGIN
 IF OLD.course_id IS NOT NULL THEN
  INSERT INTO analytics_change(course_id,source_type,source_id) VALUES(OLD.course_id,'assignment',OLD.id);
 END IF;
 IF NEW.course_id IS NOT NULL THEN
  INSERT INTO analytics_change(course_id,source_type,source_id) VALUES(NEW.course_id,'assignment',NEW.id);
 END IF;
END$$
CREATE TRIGGER an_assignment_delete BEFORE DELETE ON assignment FOR EACH ROW
BEGIN
 IF OLD.course_id IS NOT NULL THEN
  INSERT INTO analytics_change(course_id,source_type,source_id) VALUES(OLD.course_id,'assignment',OLD.id);
 END IF;
END$$
CREATE TRIGGER an_assignment_question_insert AFTER INSERT ON assignment_question FOR EACH ROW
BEGIN
 IF NEW.course_id IS NOT NULL THEN
  INSERT INTO analytics_change(course_id,source_type,source_id) VALUES(NEW.course_id,'assignment_question',NEW.id);
 END IF;
END$$
CREATE TRIGGER an_assignment_question_update AFTER UPDATE ON assignment_question FOR EACH ROW
BEGIN
 IF OLD.course_id IS NOT NULL THEN
  INSERT INTO analytics_change(course_id,source_type,source_id) VALUES(OLD.course_id,'assignment_question',OLD.id);
 END IF;
 IF NEW.course_id IS NOT NULL THEN
  INSERT INTO analytics_change(course_id,source_type,source_id) VALUES(NEW.course_id,'assignment_question',NEW.id);
 END IF;
END$$
CREATE TRIGGER an_assignment_question_delete BEFORE DELETE ON assignment_question FOR EACH ROW
BEGIN
 IF OLD.course_id IS NOT NULL THEN
  INSERT INTO analytics_change(course_id,source_type,source_id) VALUES(OLD.course_id,'assignment_question',OLD.id);
 END IF;
END$$
CREATE TRIGGER an_knowledge_points_insert AFTER INSERT ON knowledge_points FOR EACH ROW
BEGIN
 IF NEW.course_id IS NOT NULL THEN
  INSERT INTO analytics_change(course_id,source_type,source_id) VALUES(NEW.course_id,'knowledge_points',NEW.id);
 END IF;
END$$
CREATE TRIGGER an_knowledge_points_update AFTER UPDATE ON knowledge_points FOR EACH ROW
BEGIN
 IF OLD.course_id IS NOT NULL THEN
  INSERT INTO analytics_change(course_id,source_type,source_id) VALUES(OLD.course_id,'knowledge_points',OLD.id);
 END IF;
 IF NEW.course_id IS NOT NULL THEN
  INSERT INTO analytics_change(course_id,source_type,source_id) VALUES(NEW.course_id,'knowledge_points',NEW.id);
 END IF;
END$$
CREATE TRIGGER an_knowledge_points_delete BEFORE DELETE ON knowledge_points FOR EACH ROW
BEGIN
 IF OLD.course_id IS NOT NULL THEN
  INSERT INTO analytics_change(course_id,source_type,source_id) VALUES(OLD.course_id,'knowledge_points',OLD.id);
 END IF;
END$$
CREATE TRIGGER an_submission_insert AFTER INSERT ON submission FOR EACH ROW
BEGIN
 IF NEW.course_id IS NOT NULL THEN
  INSERT INTO analytics_change(course_id,source_type,source_id) VALUES(NEW.course_id,'submission',NEW.id);
 END IF;
END$$
CREATE TRIGGER an_submission_update AFTER UPDATE ON submission FOR EACH ROW
BEGIN
 IF OLD.course_id IS NOT NULL THEN
  INSERT INTO analytics_change(course_id,source_type,source_id) VALUES(OLD.course_id,'submission',OLD.id);
 END IF;
 IF NEW.course_id IS NOT NULL THEN
  INSERT INTO analytics_change(course_id,source_type,source_id) VALUES(NEW.course_id,'submission',NEW.id);
 END IF;
END$$
CREATE TRIGGER an_submission_delete BEFORE DELETE ON submission FOR EACH ROW
BEGIN
 IF OLD.course_id IS NOT NULL THEN
  INSERT INTO analytics_change(course_id,source_type,source_id) VALUES(OLD.course_id,'submission',OLD.id);
 END IF;
END$$
CREATE TRIGGER an_grade_insert AFTER INSERT ON grade FOR EACH ROW
BEGIN
 IF NEW.course_id IS NOT NULL THEN
  INSERT INTO analytics_change(course_id,source_type,source_id) VALUES(NEW.course_id,'grade',NEW.id);
 END IF;
END$$
CREATE TRIGGER an_grade_update AFTER UPDATE ON grade FOR EACH ROW
BEGIN
 IF OLD.course_id IS NOT NULL THEN
  INSERT INTO analytics_change(course_id,source_type,source_id) VALUES(OLD.course_id,'grade',OLD.id);
 END IF;
 IF NEW.course_id IS NOT NULL THEN
  INSERT INTO analytics_change(course_id,source_type,source_id) VALUES(NEW.course_id,'grade',NEW.id);
 END IF;
END$$
CREATE TRIGGER an_grade_delete BEFORE DELETE ON grade FOR EACH ROW
BEGIN
 IF OLD.course_id IS NOT NULL THEN
  INSERT INTO analytics_change(course_id,source_type,source_id) VALUES(OLD.course_id,'grade',OLD.id);
 END IF;
END$$
CREATE TRIGGER an_conversation_insert AFTER INSERT ON conversation FOR EACH ROW
BEGIN
 IF NEW.course_id IS NOT NULL THEN
  INSERT INTO analytics_change(course_id,source_type,source_id) VALUES(NEW.course_id,'conversation',NEW.id);
 END IF;
END$$
CREATE TRIGGER an_conversation_update AFTER UPDATE ON conversation FOR EACH ROW
BEGIN
 IF OLD.course_id IS NOT NULL THEN
  INSERT INTO analytics_change(course_id,source_type,source_id) VALUES(OLD.course_id,'conversation',OLD.id);
 END IF;
 IF NEW.course_id IS NOT NULL THEN
  INSERT INTO analytics_change(course_id,source_type,source_id) VALUES(NEW.course_id,'conversation',NEW.id);
 END IF;
END$$
CREATE TRIGGER an_conversation_delete BEFORE DELETE ON conversation FOR EACH ROW
BEGIN
 IF OLD.course_id IS NOT NULL THEN
  INSERT INTO analytics_change(course_id,source_type,source_id) VALUES(OLD.course_id,'conversation',OLD.id);
 END IF;
END$$
CREATE TRIGGER an_conversation_message_insert AFTER INSERT ON conversation_message FOR EACH ROW
BEGIN
 IF NEW.course_id IS NOT NULL THEN
  INSERT INTO analytics_change(course_id,source_type,source_id) VALUES(NEW.course_id,'conversation_message',NEW.id);
 END IF;
END$$
CREATE TRIGGER an_conversation_message_update AFTER UPDATE ON conversation_message FOR EACH ROW
BEGIN
 IF OLD.course_id IS NOT NULL THEN
  INSERT INTO analytics_change(course_id,source_type,source_id) VALUES(OLD.course_id,'conversation_message',OLD.id);
 END IF;
 IF NEW.course_id IS NOT NULL THEN
  INSERT INTO analytics_change(course_id,source_type,source_id) VALUES(NEW.course_id,'conversation_message',NEW.id);
 END IF;
END$$
CREATE TRIGGER an_conversation_message_delete BEFORE DELETE ON conversation_message FOR EACH ROW
BEGIN
 IF OLD.course_id IS NOT NULL THEN
  INSERT INTO analytics_change(course_id,source_type,source_id) VALUES(OLD.course_id,'conversation_message',OLD.id);
 END IF;
END$$
CREATE TRIGGER an_tutor_interaction_insert AFTER INSERT ON tutor_interaction FOR EACH ROW
BEGIN
 IF NEW.course_id IS NOT NULL THEN
  INSERT INTO analytics_change(course_id,source_type,source_id) VALUES(NEW.course_id,'tutor_interaction',NEW.id);
 END IF;
END$$
CREATE TRIGGER an_tutor_interaction_update AFTER UPDATE ON tutor_interaction FOR EACH ROW
BEGIN
 IF OLD.course_id IS NOT NULL THEN
  INSERT INTO analytics_change(course_id,source_type,source_id) VALUES(OLD.course_id,'tutor_interaction',OLD.id);
 END IF;
 IF NEW.course_id IS NOT NULL THEN
  INSERT INTO analytics_change(course_id,source_type,source_id) VALUES(NEW.course_id,'tutor_interaction',NEW.id);
 END IF;
END$$
CREATE TRIGGER an_tutor_interaction_delete BEFORE DELETE ON tutor_interaction FOR EACH ROW
BEGIN
 IF OLD.course_id IS NOT NULL THEN
  INSERT INTO analytics_change(course_id,source_type,source_id) VALUES(OLD.course_id,'tutor_interaction',OLD.id);
 END IF;
END$$
CREATE TRIGGER an_answer_feedback_insert AFTER INSERT ON answer_feedback FOR EACH ROW
BEGIN
 IF NEW.course_id IS NOT NULL THEN
  INSERT INTO analytics_change(course_id,source_type,source_id) VALUES(NEW.course_id,'answer_feedback',NEW.id);
 END IF;
END$$
CREATE TRIGGER an_answer_feedback_update AFTER UPDATE ON answer_feedback FOR EACH ROW
BEGIN
 IF OLD.course_id IS NOT NULL THEN
  INSERT INTO analytics_change(course_id,source_type,source_id) VALUES(OLD.course_id,'answer_feedback',OLD.id);
 END IF;
 IF NEW.course_id IS NOT NULL THEN
  INSERT INTO analytics_change(course_id,source_type,source_id) VALUES(NEW.course_id,'answer_feedback',NEW.id);
 END IF;
END$$
CREATE TRIGGER an_answer_feedback_delete BEFORE DELETE ON answer_feedback FOR EACH ROW
BEGIN
 IF OLD.course_id IS NOT NULL THEN
  INSERT INTO analytics_change(course_id,source_type,source_id) VALUES(OLD.course_id,'answer_feedback',OLD.id);
 END IF;
END$$
CREATE TRIGGER an_review_job_insert AFTER INSERT ON review_job FOR EACH ROW
BEGIN
 IF NEW.course_id IS NOT NULL THEN
  INSERT INTO analytics_change(course_id,source_type,source_id) VALUES(NEW.course_id,'review_job',NEW.id);
 END IF;
END$$
CREATE TRIGGER an_review_job_update AFTER UPDATE ON review_job FOR EACH ROW
BEGIN
 IF OLD.course_id IS NOT NULL THEN
  INSERT INTO analytics_change(course_id,source_type,source_id) VALUES(OLD.course_id,'review_job',OLD.id);
 END IF;
 IF NEW.course_id IS NOT NULL THEN
  INSERT INTO analytics_change(course_id,source_type,source_id) VALUES(NEW.course_id,'review_job',NEW.id);
 END IF;
END$$
CREATE TRIGGER an_review_job_delete BEFORE DELETE ON review_job FOR EACH ROW
BEGIN
 IF OLD.course_id IS NOT NULL THEN
  INSERT INTO analytics_change(course_id,source_type,source_id) VALUES(OLD.course_id,'review_job',OLD.id);
 END IF;
END$$
CREATE TRIGGER an_async_job_insert AFTER INSERT ON async_job FOR EACH ROW
BEGIN
 IF NEW.course_id IS NOT NULL AND NEW.job_type IN ('REVIEW_DOCUMENT','REVIEW_ASSIGNMENT','REVIEW_CODE') THEN
  INSERT INTO analytics_change(course_id,source_type,source_id) VALUES(NEW.course_id,'async_job',NEW.id);
 END IF;
END$$
CREATE TRIGGER an_async_job_update AFTER UPDATE ON async_job FOR EACH ROW
BEGIN
 IF OLD.course_id IS NOT NULL AND OLD.job_type IN ('REVIEW_DOCUMENT','REVIEW_ASSIGNMENT','REVIEW_CODE') THEN
  INSERT INTO analytics_change(course_id,source_type,source_id) VALUES(OLD.course_id,'async_job',OLD.id);
 END IF;
 IF NEW.course_id IS NOT NULL AND NEW.job_type IN ('REVIEW_DOCUMENT','REVIEW_ASSIGNMENT','REVIEW_CODE') THEN
  INSERT INTO analytics_change(course_id,source_type,source_id) VALUES(NEW.course_id,'async_job',NEW.id);
 END IF;
END$$
CREATE TRIGGER an_async_job_delete BEFORE DELETE ON async_job FOR EACH ROW
BEGIN
 IF OLD.course_id IS NOT NULL AND OLD.job_type IN ('REVIEW_DOCUMENT','REVIEW_ASSIGNMENT','REVIEW_CODE') THEN
  INSERT INTO analytics_change(course_id,source_type,source_id) VALUES(OLD.course_id,'async_job',OLD.id);
 END IF;
END$$
CREATE TRIGGER an_rubric_insert AFTER INSERT ON rubric FOR EACH ROW
BEGIN
 IF (SELECT course_id FROM assignment WHERE id=NEW.assignment_id) IS NOT NULL THEN
  INSERT INTO analytics_change(course_id,source_type,source_id) VALUES((SELECT course_id FROM assignment WHERE id=NEW.assignment_id),'rubric',NEW.id);
 END IF;
END$$
CREATE TRIGGER an_rubric_update AFTER UPDATE ON rubric FOR EACH ROW
BEGIN
 IF (SELECT course_id FROM assignment WHERE id=OLD.assignment_id) IS NOT NULL THEN
  INSERT INTO analytics_change(course_id,source_type,source_id) VALUES((SELECT course_id FROM assignment WHERE id=OLD.assignment_id),'rubric',OLD.id);
 END IF;
 IF (SELECT course_id FROM assignment WHERE id=NEW.assignment_id) IS NOT NULL THEN
  INSERT INTO analytics_change(course_id,source_type,source_id) VALUES((SELECT course_id FROM assignment WHERE id=NEW.assignment_id),'rubric',NEW.id);
 END IF;
END$$
CREATE TRIGGER an_rubric_delete BEFORE DELETE ON rubric FOR EACH ROW
BEGIN
 IF (SELECT course_id FROM assignment WHERE id=OLD.assignment_id) IS NOT NULL THEN
  INSERT INTO analytics_change(course_id,source_type,source_id) VALUES((SELECT course_id FROM assignment WHERE id=OLD.assignment_id),'rubric',OLD.id);
 END IF;
END$$
CREATE TRIGGER an_rubric_item_insert AFTER INSERT ON rubric_item FOR EACH ROW
BEGIN
 IF (SELECT a.course_id FROM rubric r JOIN assignment a ON a.id=r.assignment_id WHERE r.id=NEW.rubric_id) IS NOT NULL THEN
  INSERT INTO analytics_change(course_id,source_type,source_id) VALUES((SELECT a.course_id FROM rubric r JOIN assignment a ON a.id=r.assignment_id WHERE r.id=NEW.rubric_id),'rubric_item',NEW.id);
 END IF;
END$$
CREATE TRIGGER an_rubric_item_update AFTER UPDATE ON rubric_item FOR EACH ROW
BEGIN
 IF (SELECT a.course_id FROM rubric r JOIN assignment a ON a.id=r.assignment_id WHERE r.id=OLD.rubric_id) IS NOT NULL THEN
  INSERT INTO analytics_change(course_id,source_type,source_id) VALUES((SELECT a.course_id FROM rubric r JOIN assignment a ON a.id=r.assignment_id WHERE r.id=OLD.rubric_id),'rubric_item',OLD.id);
 END IF;
 IF (SELECT a.course_id FROM rubric r JOIN assignment a ON a.id=r.assignment_id WHERE r.id=NEW.rubric_id) IS NOT NULL THEN
  INSERT INTO analytics_change(course_id,source_type,source_id) VALUES((SELECT a.course_id FROM rubric r JOIN assignment a ON a.id=r.assignment_id WHERE r.id=NEW.rubric_id),'rubric_item',NEW.id);
 END IF;
END$$
CREATE TRIGGER an_rubric_item_delete BEFORE DELETE ON rubric_item FOR EACH ROW
BEGIN
 IF (SELECT a.course_id FROM rubric r JOIN assignment a ON a.id=r.assignment_id WHERE r.id=OLD.rubric_id) IS NOT NULL THEN
  INSERT INTO analytics_change(course_id,source_type,source_id) VALUES((SELECT a.course_id FROM rubric r JOIN assignment a ON a.id=r.assignment_id WHERE r.id=OLD.rubric_id),'rubric_item',OLD.id);
 END IF;
END$$
CREATE TRIGGER an_feedback_insert AFTER INSERT ON feedback FOR EACH ROW
BEGIN
 IF (SELECT course_id FROM grade WHERE id=NEW.grade_id) IS NOT NULL THEN
  INSERT INTO analytics_change(course_id,source_type,source_id) VALUES((SELECT course_id FROM grade WHERE id=NEW.grade_id),'feedback',NEW.id);
 END IF;
END$$
CREATE TRIGGER an_feedback_update AFTER UPDATE ON feedback FOR EACH ROW
BEGIN
 IF (SELECT course_id FROM grade WHERE id=OLD.grade_id) IS NOT NULL THEN
  INSERT INTO analytics_change(course_id,source_type,source_id) VALUES((SELECT course_id FROM grade WHERE id=OLD.grade_id),'feedback',OLD.id);
 END IF;
 IF (SELECT course_id FROM grade WHERE id=NEW.grade_id) IS NOT NULL THEN
  INSERT INTO analytics_change(course_id,source_type,source_id) VALUES((SELECT course_id FROM grade WHERE id=NEW.grade_id),'feedback',NEW.id);
 END IF;
END$$
CREATE TRIGGER an_feedback_delete BEFORE DELETE ON feedback FOR EACH ROW
BEGIN
 IF (SELECT course_id FROM grade WHERE id=OLD.grade_id) IS NOT NULL THEN
  INSERT INTO analytics_change(course_id,source_type,source_id) VALUES((SELECT course_id FROM grade WHERE id=OLD.grade_id),'feedback',OLD.id);
 END IF;
END$$
DELIMITER ;
