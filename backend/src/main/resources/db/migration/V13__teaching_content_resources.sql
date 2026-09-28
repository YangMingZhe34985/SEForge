-- Register independent documents without copying their existing MinIO objects.
INSERT INTO course_resources(version,course_id,chapter_id,uploader_id,name,description,resource_type,object_key,content_type,size_bytes,status,created_at,updated_at)
SELECT 0,d.course_id,d.chapter_id,d.uploader_id,d.original_name,NULL,'DOCUMENT',d.object_key,d.media_type,d.size_bytes,
       IF(d.status='DELETED','DELETED','ACTIVE'),d.created_at,d.updated_at
FROM knowledge_document d LEFT JOIN course_resources r ON r.object_key=d.object_key
WHERE d.resource_id IS NULL AND r.id IS NULL;
UPDATE knowledge_document d JOIN course_resources r ON r.object_key=d.object_key AND r.course_id=d.course_id
SET d.resource_id=r.id WHERE d.resource_id IS NULL;
ALTER TABLE knowledge_document DROP INDEX uk_knowledge_document_checksum;
CREATE INDEX idx_document_checksum ON knowledge_document(course_id,checksum);
ALTER TABLE course_resources ADD COLUMN external_url VARCHAR(2048) NULL;
ALTER TABLE knowledge_points ADD COLUMN importance VARCHAR(20) NULL, ADD COLUMN source_citations JSON NULL;
-- Only teacher confirmation writes here. Generated drafts live temporarily in Redis, not MySQL.
CREATE TABLE knowledge_point_confirmation (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 version BIGINT NOT NULL DEFAULT 0, created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL,
 course_id BIGINT NOT NULL, chapter_id BIGINT NOT NULL, owner_id BIGINT NOT NULL,
 draft_key VARCHAR(64) NOT NULL UNIQUE, trace_id BIGINT NULL, result_ids JSON NOT NULL,
 FOREIGN KEY (course_id) REFERENCES courses(id),
 FOREIGN KEY (chapter_id) REFERENCES course_chapters(id) ON DELETE CASCADE,
 FOREIGN KEY (owner_id) REFERENCES users(id),
 INDEX idx_kp_confirmation_scope(course_id,chapter_id,owner_id)
);
