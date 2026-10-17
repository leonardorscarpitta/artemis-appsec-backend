-- Where the repository's stack came from: PENDING (automatic detection requested), DETECTED (from the repository's
-- manifest files on a successful access check) or MANUAL (chosen by a user; never overwritten by detection).
ALTER TABLE project ADD COLUMN stack_source VARCHAR(16) NOT NULL DEFAULT 'MANUAL';
