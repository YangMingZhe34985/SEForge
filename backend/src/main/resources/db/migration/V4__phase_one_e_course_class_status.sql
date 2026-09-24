-- Existing classes remain active. Closing a class is reversible and retains references.
ALTER TABLE course_classes ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE;
