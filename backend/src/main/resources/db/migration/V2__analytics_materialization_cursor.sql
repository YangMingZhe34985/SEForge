ALTER TABLE analytics_snapshot
    ADD COLUMN source_cursor VARCHAR(64) NULL AFTER metric_type;

UPDATE analytics_snapshot
SET source_cursor = CONCAT('legacy-', id)
WHERE source_cursor IS NULL;

ALTER TABLE analytics_snapshot
    MODIFY COLUMN source_cursor VARCHAR(64) NOT NULL;

CREATE INDEX idx_analytics_snapshot_source
    ON analytics_snapshot (course_id, metric_type, source_cursor);
