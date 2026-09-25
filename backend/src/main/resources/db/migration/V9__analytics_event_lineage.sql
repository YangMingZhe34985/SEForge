ALTER TABLE analytics_change ADD COLUMN applied_revision BIGINT NULL AFTER applied;
CREATE INDEX idx_analytics_change_revision ON analytics_change(course_id,applied_revision,id);
