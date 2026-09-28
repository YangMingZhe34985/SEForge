-- Canonical names retain existing ownership and file bytes; never guess image meaning from order.
UPDATE assignment_media SET purpose='QUESTION_CONTENT' WHERE purpose='CONTENT';
UPDATE assignment_media SET purpose='REFERENCE_ANSWER' WHERE purpose='REFERENCE';
ALTER TABLE question_import_draft ADD COLUMN source_sections_json LONGTEXT NULL;
